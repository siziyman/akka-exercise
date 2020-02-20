package dev.chikanov.exercise;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class NameActor {
    private static final Random RANDOM = new Random();
    private final List<String> statuses;
    private ActorContext<Command> ctx;
    private String name;
    private String status;
    private long timestamp = 0;

    private NameActor(ActorContext<Command> ctx, String name, List<String> statuses) {
        this.ctx = ctx;
        this.name = name;
        this.statuses = statuses;
    }

    public static Behavior<Command> create(String name, List<String> statuses) {
        return Behaviors.setup(ctx -> new NameActor(ctx, name, statuses).ready());
    }

    public Behavior<Command> ready() {
        scheduleStatusUpdate();
        return Behaviors.receive(Command.class).onMessage(NameRequest.class, this::onNameReceived).onMessage(ChangeStatus.class, this::onChangeStatus).build();
    }

    private Behavior<Command> onChangeStatus(ChangeStatus message) {
        this.status = message.status;
        this.timestamp = this.timestamp + message.delay;
        scheduleStatusUpdate();
        return Behaviors.same();
    }

    private void scheduleStatusUpdate() {
        Duration delay = Duration.ofMillis(3000 + RANDOM.nextInt(5) * 500); // 3 + ([0, 5) * 0.5) seconds - equals [3, 5] seconds
        ctx.scheduleOnce(delay, ctx.getSelf(), new ChangeStatus(this.statuses.get(RANDOM.nextInt(this.statuses.size())), delay.toMillis()));
    }

    private Behavior<Command> onNameReceived(NameRequest request) {
        this.ctx.getLog().info("HELLO: {}", this.name);
        request.replyTo.tell(new NameStatusResponse(Optional.of(new NameActor.NameStatus(this.name, this.status, this.timestamp))));
        return Behaviors.same();
    }

    interface Command {
    }

    public static class NameRequest implements Command {
        public final ActorRef<NameStatusResponse> replyTo;

        public NameRequest(ActorRef<NameStatusResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class NameStatusResponse implements Command {
        @Override
        public String toString() {
            return maybeNameStatus.map(String::valueOf).orElse("");
        }

        public final Optional<NameStatus> maybeNameStatus;

        public NameStatusResponse(Optional<NameStatus> maybeNameStatus) {
            this.maybeNameStatus = maybeNameStatus;
        }
    }

    public static final class NameStatus{
        public final String name;
        public final String status;
        public final long timestamp;

        public NameStatus(String name, String status, long timestamp) {
            this.name = name;
            this.status = status;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "NameStatus{" +
                    "name='" + name + '\'' +
                    ", status='" + status + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    public static final class ChangeStatus implements Command {
        public final String status;
        public final long delay;

        public ChangeStatus(String status, long delay) {
            this.status = status;
            this.delay = delay;
        }
    }

}
