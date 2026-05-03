package com.B2B.service;

import com.B2B.model.PasskeyCredential;
import com.B2B.model.business;
import com.B2B.repository.BusinessRepository;
import com.B2B.repository.PasskeyRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WebAuthnService {

    private final PasskeyRepository passkeyRepository;
    private final BusinessRepository businessRepository;
    private final RelyingParty relyingParty;

    // Temporary storage for challenges since Spring Boot is stateless
    private final Map<String, String> challenges = new HashMap<>();
    private final Map<String, AssertionRequest> assertionRequests = new HashMap<>();

    @Autowired
    public WebAuthnService(PasskeyRepository passkeyRepository, BusinessRepository businessRepository) {
        this.passkeyRepository = passkeyRepository;
        this.businessRepository = businessRepository;

        System.out.println("Initializing WebAuthn RelyingParty...");

        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id("localhost")
                .name("StockBridge B2B")
                .build();

        CredentialRepository credentialRepository = new CredentialRepository() {
            @Override
            public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
                return businessRepository.findByEmail(username).map(b -> passkeyRepository.findByBusiness(b).stream()
                        .map(c -> {
                            try {
                                return PublicKeyCredentialDescriptor.builder()
                                        .id(ByteArray.fromBase64Url(c.getCredentialId()))
                                        .type(PublicKeyCredentialType.PUBLIC_KEY)
                                        .build();
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
            }

            @Override
            public Optional<ByteArray> getUserHandleForUsername(String username) {
                return businessRepository.findByEmail(username).map(b -> new ByteArray(b.getEmail().getBytes()));
            }

            @Override
            public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
                return Optional.of(new String(userHandle.getBytes()));
            }

            @Override
            public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
                System.out.println("WebAuthn Lookup credentialId: " + credentialId.getBase64Url());
                Optional<PasskeyCredential> credOpt = passkeyRepository.findByCredentialId(credentialId.getBase64Url());
                if (!credOpt.isPresent()) {
                    System.err.println("WebAuthn Credential not found in database lookup!");
                    return Optional.empty();
                }

                PasskeyCredential cred = credOpt.get();
                System.out.println("WebAuthn Credential found for business: " + cred.getBusiness().getEmail());
                return Optional.of(RegisteredCredential.builder()
                        .credentialId(credentialId)
                        .userHandle(userHandle)
                        .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                        .signatureCount(cred.getSignatureCount())
                        .build());
            }

            @Override
            public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
                String targetId = credentialId.getBase64Url();
                System.out.println("WebAuthn LookupAll for ID: " + targetId);

                Optional<PasskeyCredential> found = passkeyRepository.findByCredentialId(targetId);

                if (found.isPresent()) {
                    PasskeyCredential cred = found.get();
                    System.out.println("WebAuthn MATCH FOUND for " + cred.getBusiness().getEmail());
                    ByteArray userHandle = new ByteArray(cred.getBusiness().getEmail().getBytes());
                    return Collections.singleton(RegisteredCredential.builder()
                            .credentialId(credentialId)
                            .userHandle(userHandle)
                            .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                            .signatureCount(cred.getSignatureCount())
                            .build());
                } else {
                    List<PasskeyCredential> all = passkeyRepository.findAll();
                    System.err.println("WebAuthn ERROR: No credential found in DB with ID: " + targetId);
                    System.err.println("WebAuthn Total credentials in DB: " + all.size());
                    for (PasskeyCredential p : all) {
                        System.err.println(" - Stored ID: " + p.getCredentialId() + " (Business: "
                                + p.getBusiness().getEmail() + ")");
                    }
                    return Collections.emptySet();
                }
            }
        };

        Set<String> allowedOrigins = new HashSet<>();
        for (int port = 5173; port <= 5190; port++) {
            allowedOrigins.add("http://localhost:" + port);
            allowedOrigins.add("http://127.0.0.1:" + port);
        }

        this.relyingParty = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(allowedOrigins)
                .build();
    }

    public PublicKeyCredentialCreationOptions startRegistration(String email) throws Exception {
        ByteArray userId = ByteArray
                .fromBase64Url(Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes()));

        StartRegistrationOptions opts = StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(email)
                        .displayName(email)
                        .id(userId)
                        .build())
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(opts);
        challenges.put(email, options.getChallenge().getBase64Url());
        return options;
    }

    @Transactional
    public void finishRegistration(String email, business savedBusiness, String attestationJson) throws Exception {
        String savedChallenge = challenges.get(email);
        if (savedChallenge == null)
            throw new RuntimeException("No active registration for this email");

        PublicKeyCredentialCreationOptions creationOptions = PublicKeyCredentialCreationOptions.builder()
                .rp(relyingParty.getIdentity())
                .user(UserIdentity.builder().name(email).displayName(email)
                        .id(ByteArray.fromBase64Url(
                                Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes())))
                        .build())
                .challenge(ByteArray.fromBase64Url(savedChallenge))
                .pubKeyCredParams(Collections.singletonList(PublicKeyCredentialParameters.builder()
                        .alg(COSEAlgorithmIdentifier.ES256).type(PublicKeyCredentialType.PUBLIC_KEY).build()))
                .build();

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = PublicKeyCredential
                .parseRegistrationResponseJson(attestationJson);

        RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                .request(creationOptions)
                .response(pkc)
                .build());

        String newCredentialId = result.getKeyId().getId().getBase64Url();
        System.out.println("WebAuthn SAVING NEW CREDENTIAL: " + newCredentialId + " for " + email);

        PasskeyCredential cred = new PasskeyCredential();
        cred.setCredentialId(newCredentialId);
        cred.setPublicKeyCose(result.getPublicKeyCose().getBytes());
        cred.setSignatureCount(result.getSignatureCount());
        cred.setBusiness(savedBusiness);
        passkeyRepository.save(cred);

        challenges.remove(email);
        System.out.println("WebAuthn Registration SUCCESS for " + email);
    }

    public PublicKeyCredentialRequestOptions startLogin(String email) throws Exception {
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(email)
                .build());

        assertionRequests.put(email, request);
        return request.getPublicKeyCredentialRequestOptions();
    }

    @Transactional
    public business finishLogin(String email, String assertionJson) throws Exception {
        System.out.println("Finishing login for email: " + email);
        AssertionRequest request = assertionRequests.get(email);
        if (request == null) {
            System.err.println("No active login request found in memory for: " + email);
            throw new RuntimeException("No active login for this email");
        }

        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc = PublicKeyCredential
                    .parseAssertionResponseJson(assertionJson);

            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(pkc)
                    .build());

            if (result.isSuccess()) {
                System.out.println("Assertion success for: " + email);
                Optional<PasskeyCredential> credOpt = passkeyRepository
                        .findByCredentialId(result.getCredential().getCredentialId().getBase64Url());
                if (credOpt.isPresent()) {
                    PasskeyCredential cred = credOpt.get();
                    cred.setSignatureCount(result.getSignatureCount());
                    passkeyRepository.save(cred);
                    assertionRequests.remove(email);
                    System.out.println(
                            "Login completed successfully for business: " + cred.getBusiness().getBusinessName());
                    return cred.getBusiness();
                } else {
                    System.err.println("Credential ID not found in database: "
                            + result.getCredential().getCredentialId().getBase64Url());
                }
            } else {
                System.err.println("Assertion reported failure for: " + email);
            }
        } catch (Exception e) {
            System.err.println("Error during finishAssertion: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        throw new RuntimeException("Login failed");
    }
}
