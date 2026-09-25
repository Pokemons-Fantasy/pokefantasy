package com.villu.pokefantasy.mediator;

import com.villu.pokefantasy.ports.TransactionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringMediatorTest {

    record TestCommand(String value) implements Command {}
    record UnknownCommand() implements Command {}

    private final AtomicInteger transactions = new AtomicInteger();
    private final TransactionPort transactionPort = new TransactionPort() {
        @Override
        public <T> T execute(TransactionalWork<T> work) throws Exception {
            transactions.incrementAndGet();
            return work.run();
        }
    };

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
        mediator = new SpringMediator(List.of(handler), transactionPort);
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
    void send_knownCommand_runsHandlerInsideTransaction() throws Exception {
        mediator.send(new TestCommand("hello"));
        assertThat(transactions).hasValue(1);
    }

    @Test
    void send_invalidCommand_doesNotOpenTransaction() {
        assertThatThrownBy(() -> mediator.send(new UnknownCommand()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transactions).hasValue(0);
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
        assertThatThrownBy(() -> new SpringMediator(List.of(h1, h2), transactionPort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate handler");
    }
}
