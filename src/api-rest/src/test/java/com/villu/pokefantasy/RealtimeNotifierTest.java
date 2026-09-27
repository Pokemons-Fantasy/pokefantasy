package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.RealtimeEventPort;
import com.villu.pokefantasy.ports.RealtimeEventPort.Audience;
import com.villu.pokefantasy.ports.RealtimeEventPort.RealtimeEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeNotifierTest {

    private final RealtimeEventPort port = mock(RealtimeEventPort.class);
    private final RealtimeNotifier notifier = new RealtimeNotifier(port);

    @Test
    void draftUpdated_publishesLeagueEvent() {
        notifier.draftUpdated("l1");

        verify(port).publish(new RealtimeEvent(Audience.LEAGUE, "l1", "draft-updated", "{}"));
    }

    @Test
    void notifyUser_publishesUserEvent() {
        notifier.notifyUser("misty", "steal", "{\"x\":1}");

        verify(port).publish(new RealtimeEvent(Audience.USER, "misty", "steal", "{\"x\":1}"));
    }
}
