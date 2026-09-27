package com.villu.pokefantasy.mediator;

import com.villu.pokefantasy.ports.CommandMetricsPort;
import com.villu.pokefantasy.ports.TransactionPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SpringMediator implements Mediator {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;
    private final TransactionPort transactionPort;
    private final CommandMetricsPort commandMetricsPort;

    public SpringMediator(List<CommandHandler<?, ?>> handlers, TransactionPort transactionPort,
                          CommandMetricsPort commandMetricsPort) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        CommandHandler::commandType,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("Duplicate handler for command type: " + a.commandType());
                        }
                ));
        this.transactionPort = transactionPort;
        this.commandMetricsPort = commandMetricsPort;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R, C extends Command> R send(C command) throws Exception {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        CommandHandler<C, R> handler = (CommandHandler<C, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for command type: " + command.getClass().getName());
        }
        long start = System.nanoTime();
        Throwable failure = null;
        try {
            // Cada comando es una unidad atómica: si falla cualquier escritura, no se persiste ninguna.
            return transactionPort.execute(() -> handler.handle(command));
        } catch (Exception | Error e) {
            failure = e;
            throw e;
        } finally {
            commandMetricsPort.record(command.getClass().getSimpleName(),
                    Duration.ofNanos(System.nanoTime() - start), failure);
        }
    }
}
