package com.villu.pokefantasy.mediator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringMediatorTest {

    record TestCommand(String value) implements Command {}
    record UnknownCommand() implements Command {}

    private SpringMediator mediator;

    @BeforeEach
    void setUp() {
        CommandHandler<TestCommand, String> handler = new CommandHandler<>() {
            @Override
            public String handle(TestCommand command) {
                return "result-" + command.value();
            }
            @Override
            public Class<TestCommand> commandType() {
                return TestCommand.class;
            }
        };
        mediator = new SpringMediator(List.of(handler));
    }

    @Test
    void send_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> mediator.send(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void send_unknownCommand_throwsIllegalState() {
        assertThatThrownBy(() -> mediator.send(new UnknownCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No handler registered");
    }

    @Test
    void send_knownCommand_returnsHandlerResult() throws Exception {
        String result = mediator.send(new TestCommand("hello"));
        assertThat(result).isEqualTo("result-hello");
    }

    @Test
    void constructor_duplicateHandlers_throwsIllegalState() {
        CommandHandler<TestCommand, String> h1 = new CommandHandler<>() {
            @Override public String handle(TestCommand cmd) { return "a"; }
            @Override public Class<TestCommand> commandType() { return TestCommand.class; }
        };
        CommandHandler<TestCommand, String> h2 = new CommandHandler<>() {
            @Override public String handle(TestCommand cmd) { return "b"; }
            @Override public Class<TestCommand> commandType() { return TestCommand.class; }
        };
        assertThatThrownBy(() -> new SpringMediator(List.of(h1, h2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate handler");
    }
}
