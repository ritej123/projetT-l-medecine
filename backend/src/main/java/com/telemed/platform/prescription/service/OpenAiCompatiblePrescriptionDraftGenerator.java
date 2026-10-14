package com.telemed.platform.prescription.service;

import java.util.List;
import java.util.Map;
import java.net.URI;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OpenAiCompatiblePrescriptionDraftGenerator implements PrescriptionDraftGenerator {

    private static final String SYSTEM_PROMPT = """
            Vous êtes un assistant de rédaction clinique. Produisez uniquement un brouillon d'ordonnance en français,
            à partir des seules informations présentes dans les notes anonymisées. N'inventez jamais de médicament,
            diagnostic, dose, fréquence, durée, voie d'administration ou contre-indication. Si une information
            indispensable manque ou est ambiguë, indiquez clairement « À compléter par le médecin » au lieu de la
            deviner. N'incluez aucune identité. Commencez obligatoirement par « BROUILLON NON VALIDÉ — À RELIRE ET
            VALIDER PAR LE MÉDECIN ». Ce texte ne constitue pas une décision médicale.
            """;

    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiCompatiblePrescriptionDraftGenerator(
            RestClient.Builder restClientBuilder,
            @Value("${app.llm.prescription.endpoint:}") String endpoint,
            @Value("${app.llm.prescription.api-key:}") String apiKey,
            @Value("${app.llm.prescription.model:}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String generateDraft(String anonymizedNotes) {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new ExternalLlmUnavailableException("La génération assistée n'est pas configurée.");
        }
        try {
            URI target = URI.create(endpoint);
            if (!target.isAbsolute()
                    || !("https".equalsIgnoreCase(target.getScheme())
                            || ("http".equalsIgnoreCase(target.getScheme())
                                    && ("localhost".equalsIgnoreCase(target.getHost())
                                            || "127.0.0.1".equals(target.getHost()))))
                    || !StringUtils.hasText(target.getHost())
                    || target.getUserInfo() != null
                    || target.getFragment() != null) {
                throw new ExternalLlmUnavailableException("L'URL du fournisseur LLM doit être une URL HTTPS valide.");
            }
            JsonNode response = restClient.post()
                    .uri(target)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(Map.of(
                            "model", model,
                            "temperature", 0,
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", anonymizedNotes))))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content");
            if (content == null || !content.isTextual() || !StringUtils.hasText(content.asText())) {
                throw new ExternalLlmUnavailableException("Le fournisseur n'a pas retourné de brouillon exploitable.");
            }
            String draft = content.asText().trim();
            if (draft.length() > 10000) {
                throw new ExternalLlmUnavailableException("Le brouillon généré dépasse la taille autorisée.");
            }
            if (!draft.startsWith("BROUILLON NON VALIDÉ")) {
                draft = "BROUILLON NON VALIDÉ — À RELIRE ET VALIDER PAR LE MÉDECIN\n\n" + draft;
            }
            if (draft.length() > 10000) {
                throw new ExternalLlmUnavailableException("Le brouillon généré dépasse la taille autorisée.");
            }
            return draft;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ExternalLlmUnavailableException("Le service de génération est momentanément indisponible.");
        }
    }
}
