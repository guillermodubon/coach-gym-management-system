package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailApiEmailSenderFactory;
import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailConfigurationReadiness;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider-neutral email configuration with Gmail as the sole enabled transport. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmailProperties.class, IdentityEmailProperties.class})
class EmailConfiguration {

    @Bean
    EmailComposer emailComposer(
            EmailProperties properties,
            ObjectProvider<OrganizationIdentityQuery> organizationQueries) {
        return new EmailTemplateComposer(
                properties,
                organizationQueries.getIfAvailable(() -> Optional::empty));
    }

    @Bean
    EmailDeliveryLifecyclePolicy emailDeliveryLifecyclePolicy(EmailProperties properties) {
        return new EmailDeliveryLifecyclePolicy(
                properties.maxRetryCount(), properties.stalePendingThreshold());
    }

    @Bean
    EmailSender emailSender(
            EmailProperties properties,
            GmailApiEmailSenderFactory gmailApiEmailSenderFactory,
            GmailConfigurationReadiness gmailReadiness) {
        if (!properties.enabled()) {
            return new DisabledEmailSender();
        }
        if (!properties.isValidWhenEnabled()
                || gmailReadiness.status(true, properties.fromAddress())
                        != GmailConfigurationReadiness.Status.READY) {
            throw new IllegalStateException("Gmail email configuration is invalid.");
        }
        return gmailApiEmailSenderFactory.createSender();
    }
}
