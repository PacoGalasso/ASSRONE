package ASSRONE.backend.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Attaches a Logback {@link ListAppender} directly to {@link SecurityAuditService}'s
 * logger so tests can assert on the actual formatted {@code security_event} lines a
 * real call produces — the exact bytes a log shipper or a human grepping the file
 * would see — rather than mocking the service and losing that guarantee.
 */
public final class AuditLogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    public AuditLogCapture() {
        logger = (Logger) LoggerFactory.getLogger(SecurityAuditService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    public List<ILoggingEvent> events() {
        return appender.list;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
