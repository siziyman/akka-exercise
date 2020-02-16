package dev.chikanov.exercise;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class NameActor extends AbstractBehavior<String> {

    public static Behavior<String> create(String name) {
        return Behaviors.setup(NameActor::new);
    }

    private NameActor(ActorContext<String> context) {
        super(context);
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder().onMessage(String.class, this::onMessageReceived).build();
    }

    private NameActor onMessageReceived(String message) {
        System.out.println(message);
        return this;
    }

    public static final class NameStatus {
        public final String name;
        public final String status;
        public final long timestamp;

        public NameStatus(String name, String status, long timestamp) {
            this.name = name;
            this.status = status;
            this.timestamp = timestamp;
        }
    }

}
