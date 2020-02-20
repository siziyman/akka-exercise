package dev.chikanov.exercise;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

public class HttpServer extends AllDirectives {
    private static final Duration defaultTimeout = Duration.ofMillis(350000000);
    private final HashMap<String, ActorRef<NameActor.Command>> map = new HashMap<>();
    private final ActorContext<NameActor.NameStatus> context;
    private final Config config;

    private HttpServer(ActorContext<NameActor.NameStatus> context, Config config) {
        this.context = context;
        this.config = config;
        initActors();
    }

    public static void main(String[] args) throws IOException {
        var parsed = Config.parseConfigFromArgs(args);
        var config = parsed.orElse(Config.DEFAULT_CFG);
        ActorSystem.create(HttpServer.create(config), "HTTPServer");
    }

    private static Behavior<NameActor.NameStatus> create(Config config) {
        return Behaviors.setup(ctx -> new HttpServer(ctx, config).behavior());
    }

    private void initActors() {
        List<String> strings = List.copyOf(config.statuses);
        for (String name : config.names) {
            ActorRef<NameActor.Command> spawnedNameActor = context.spawn(NameActor.create(name, strings), name);
            context.watch(spawnedNameActor);
            map.put(name, spawnedNameActor);
        }
    }
//
//    private CompletionStage<NameActor.NameRequest> getName(String name, Duration timeout, Scheduler scheduler) {
//        return new CompletableFuture<>()
//    }

    private void startHttpServer(ActorSystem<Void> system, Config config) throws IOException {
        final Http http = Http.get(system.classicSystem());

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = this.createRoute(context.getSystem()).flow(system.classicSystem(), Materializer.matFromSystem(system));
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost("localhost", config.listenPort), Materializer.matFromSystem(system));

        System.out.printf("Server online at http://localhost:%d/", config.listenPort);
//        binding
//                .thenCompose(ServerBinding::unbind)
//                .thenAccept(unbound -> system.terminate());
    }

    private CompletionStage<NameActor.NameStatusResponse> askNameStatus(String name) {
        ActorRef<NameActor.Command> commandActorRef = map.get(name);
        if (commandActorRef == null) {
            return CompletableFuture.completedFuture(new NameActor.NameStatusResponse(Optional.empty()));
        }
        return AskPattern.ask(commandActorRef, NameActor.NameRequest::new, defaultTimeout, context.getSystem().scheduler());
    }

    private Behavior<NameActor.NameStatus> behavior() throws IOException {
        startHttpServer(this.context.getSystem(), this.config);
        return Behaviors.empty();
    }

    private Route createRoute(akka.actor.typed.ActorSystem<?> system) {
        return pathPrefix("check", () -> path(PathMatchers.segment(), (String name) -> get(() -> rejectEmptyResponse(() ->
                onSuccess(askNameStatus(name), status -> complete(String.valueOf(status))))
        ))).seal();
    }


    private static final class Config {
        private static final List<String> DEFAULT_NAMES = List.of("John", "Alex", "Juan", "Alejandro");
        private static final List<String> DEFAULT_STATUSES = List.of("asleep", "awake");
        private static final int DEFAULT_PORT = 8080;

        private static final Config DEFAULT_CFG = new Config(DEFAULT_PORT, Set.copyOf(DEFAULT_NAMES), Set.copyOf(DEFAULT_STATUSES));

        private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$"); // it will be converted back to string, but compiling pattern helps with validating correctness

        private static final String PORT_KEY = "-p";
        private static final String NAME_KEY = "-n";
        private static final String STATUS_KEY = "-s";

        private static final int MAX_PORT = 65535;
        public final int listenPort;
        public final Set<String> names;
        public final Set<String> statuses;

        private Config(int listenPort, Set<String> names, Set<String> statuses) {
            this.listenPort = listenPort;
            this.names = names;
            this.statuses = statuses;
        }

        // Parse config from args according to task description
        // if config appears to be invalid (duplicated config keys; missing port), Optional.empty() is returned,
        // therefore, top-level logic can "decide" whether to fallback to default config or return error.
        static Optional<Config> parseConfigFromArgs(String[] args) {
            var portVisited = false;
            var nameVisited = false;
            var statusVisited = false;
            var port = DEFAULT_PORT;
            Set<String> statusList = new HashSet<>();
            Set<String> namesList = new HashSet<>();
            int i = 0;
            while (i < args.length) {
                switch (args[i]) {
                    case PORT_KEY:
                        if (portVisited) {
                            return Optional.empty();
                        }
                        if (args.length > i + 1 && args[i + 1].matches(PORT_PATTERN.pattern())) {
                            var portCandidate = Integer.parseUnsignedInt(args[i + 1]);
                            if (portCandidate <= MAX_PORT) {
                                port = portCandidate;
                            }
                            i = i + 2;
                        }
                        portVisited = true;
                        break;
                    case NAME_KEY:
                        if (nameVisited) {
                            return Optional.empty();
                        }
                        i = fillNamesFromArgs(namesList, args, i, STATUS_KEY);
                        nameVisited = true;
                        break;
                    case STATUS_KEY:
                        if (statusVisited) {
                            return Optional.empty();
                        }
                        i = fillNamesFromArgs(statusList, args, i, NAME_KEY);
                        statusVisited = true;
                        break;
                }
            }
            if (statusList.isEmpty()) {
                statusList = Set.copyOf(DEFAULT_STATUSES);
            }
            if (namesList.isEmpty()) {
                namesList = Set.copyOf(DEFAULT_NAMES);
            }
            if (!portVisited) {
                return Optional.empty();
            }
            return Optional.of(new Config(port, namesList, statusList));
        }

        //fill set of names from args list; this parsing implementation is "tolerant" to tailing comma in the list.
        private static int fillNamesFromArgs(Set<String> setToFill, String[] args, int startIndex, String keyToAvoid) {
            int j = startIndex + 1;
            while (args.length > j && !(args[j].equals(PORT_KEY) || args[j].equals(keyToAvoid))) {
                var strCandidate = args[j];
                if (strCandidate.endsWith(",")) {
                    strCandidate = strCandidate.substring(0, strCandidate.length() - 1);
                }
                setToFill.add(strCandidate);
                j++;
            }
            startIndex = j;
            return startIndex;
        }
    }
}
