package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.config.CoordinatorConfig;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * MailService provides functionality to send alert emails
 * using SMTP configuration provided in {@link CoordinatorConfig.MailCfg}.
 * Typically used to notify failures in subscribers or background tasks.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class MailService {
    private final CoordinatorConfig.MailCfg cfg;
    private final Session session;

    public MailService(CoordinatorConfig.MailCfg cfg) {
        this.cfg = cfg;
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", cfg.smtpHost());
        props.put("mail.smtp.port", String.valueOf(cfg.smtpPort()));

        this.session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.from(), cfg.password());
            }
        });
    }

    public void sendAlert(String platform, String message) {
        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(cfg.from()));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(cfg.to()));
            msg.setSubject("Subscriber Alert: " + platform);
            msg.setText(message);
            Transport.send(msg);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
