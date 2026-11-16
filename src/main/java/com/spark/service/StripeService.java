package com.spark.service;

import com.spark.model.User;
import com.spark.repository.UserRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final UserRepository userRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void processWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed.");
            throw new RuntimeException("Invalid signature", e);
        }

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()) {
                StripeObject stripeObject = dataObjectDeserializer.getObject().get();
                if (stripeObject instanceof Session session) {
                    handleCheckoutSessionCompleted(session);
                }
            }
        }
    }

    private void handleCheckoutSessionCompleted(Session session) {
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;
        if (email == null) {
            log.warn("Stripe session completed but no email found.");
            return;
        }

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            user.setPro(true);
            // Default 30 days of Pro for a standard purchase
            user.setProExpiresAt(LocalDateTime.now().plusDays(30));
            user.setStripeCustomerId(session.getCustomer());
            userRepository.save(user);
            log.info("Pro activated for user via Stripe webhook: {}", email);
        }, () -> log.warn("User not found for email from Stripe: {}", email));
    }
}
