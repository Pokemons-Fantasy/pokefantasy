package com.villu.pokefantasy.mediator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SpringMediator implements Mediator {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    public SpringMediator(List<CommandHandler<?, ?>> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        CommandHandler::commandType,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("Duplicate handler for command type: " + a.commandType());
                        }
                ));
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
        return handler.handle(command);
    }
}

