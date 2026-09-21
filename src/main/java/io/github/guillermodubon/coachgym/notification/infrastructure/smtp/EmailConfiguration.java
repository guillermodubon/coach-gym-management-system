package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.notification.infrastructure.resend.ResendEmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.resend.ResendProperties;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Infrastructure wiring for the provider-neutral composer and SMTP sender. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmailProperties.class, ResendProperties.class})
class EmailConfiguration {

    @Bean
    EmailComposer emailComposer(
            EmailProperties properties,
            ObjectProvider<OrganizationIdentityQuery> organizationQueries) {
        return new EmailTemplateComposer(
                properties,
                organizationQueries.getIfAvailable(() -> () -> java.util.Optional.empty()));
    }

    @Bean
    EmailDeliveryLifecyclePolicy emailDeliveryLifecyclePolicy(EmailProperties properties) {
        return new EmailDeliveryLifecyclePolicy(
                properties.maxRetryCount(), properties.stalePendingThreshold());
    }

    @Bean
    EmailSender emailSender(
            EmailProperties properties,
            ResendProperties resendProperties) {
        if (!properties.enabled()) {
            return new DisabledEmailSender();
        }
        if ("resend".equals(properties.provider())) {
            return new ResendEmailSender(
                    ResendEmailSender.createHttpClient(resendProperties),
                    new ObjectMapper(),
                    properties,
                    resendProperties);
        }
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.smtpHost());
        mailSender.setPort(properties.smtpPort());
        if (properties.smtpUsername() != null) {
            mailSender.setUsername(properties.smtpUsername());
        }
        if (properties.smtpPassword() != null) {
            mailSender.setPassword(properties.smtpPassword());
        }
        mailSender.getJavaMailProperties().put("mail.smtp.auth",
                Boolean.toString(properties.smtpAuthEnabled()));
        mailSender.getJavaMailProperties().put("mail.smtp.starttls.enable",
                Boolean.toString(properties.smtpStarttlsEnabled()));
        mailSender.getJavaMailProperties().put("mail.smtp.connectiontimeout",
                Long.toString(properties.connectionTimeout().toMillis()));
        mailSender.getJavaMailProperties().put("mail.smtp.timeout",
                Long.toString(properties.readTimeout().toMillis()));
        mailSender.getJavaMailProperties().put("mail.smtp.writetimeout",
                Long.toString(properties.writeTimeout().toMillis()));
        return new SmtpEmailSender(mailSender, properties);
    }
}
