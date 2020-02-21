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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HttpServer extends AllDirectives {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(5000);
    private static final String HELP = "This app starts a server with set names and statuses and allows you to check their status.\n" +
            "Available configuration flags:\n" +
            "-p <port> - port to listen at. MANDATORY\n" +
            "-n <name> [, name]... - comma-separated list of names. Optional: default list is John, Alex, Juan, Alejandro\n" +
            "-s <status> [, status]... - comma-separated list of statuses. Optional: default list is awake, asleep";
    private static final String HOST = "localhost";

    private final HashMap<String, ActorRef<NameActor.Command>> map = new HashMap<>();
    private final ActorContext<NameActor.NameStatus> context;
    private final Config config;

    private HttpServer(ActorContext<NameActor.NameStatus> context, Config config) {
        this.context = context;
        this.config = config;
        initActors();
    }

    public static void main(String[] args) {
        final var parsed = Config.parseConfigFromArgs(args);
        parsed.ifPresentOrElse(
                config -> ActorSystem.create(HttpServer.create(config), "HTTPServer"),
                () -> System.err.println("ERROR: Invalid config supplied\n" + HELP));
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

    private void startHttpServer(ActorSystem<Void> system, Config config) {
        final Http http = Http.get(system.classicSystem());

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = this.createRoute().flow(system.classicSystem(), Materializer.matFromSystem(system));
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost(HOST, config.listenPort), Materializer.matFromSystem(system));

        system.log().info("Server online at http://{}:{}/", HOST, config.listenPort);
    }

    private CompletionStage<NameActor.NameStatusResponse> askNameStatus(String name) {
        ActorRef<NameActor.Command> commandActorRef = map.get(name);
        if (commandActorRef == null) {
            return CompletableFuture.completedFuture(new NameActor.NameStatusResponse(Optional.empty()));
        }
        return AskPattern.ask(commandActorRef, NameActor.NameRequest::new, DEFAULT_TIMEOUT, context.getSystem().scheduler());
    }

    private Behavior<NameActor.NameStatus> behavior() {
        startHttpServer(this.context.getSystem(), this.config);
        return Behaviors.empty();
    }

    private Route createRoute() {
        return pathPrefix("check",
                () -> path(PathMatchers.segment(),
                        (String name) -> get(
                                () -> rejectEmptyResponse(
                                        () -> onSuccess(askNameStatus(name), status -> complete(String.valueOf(status)))
                                )
                        )
                )
        ).seal();
    }



    private static final class Config {
        private static final List<String> DEFAULT_NAMES = List.of("John", "Alex", "Juan", "Alejandro");
        private static final List<String> DEFAULT_STATUSES = List.of("asleep", "awake");
        private static final int DEFAULT_PORT = 8080;
        //not used, but can be provided, if default behavior convention changes
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
            Set<String> statusSet = new HashSet<>();
            Set<String> nameSet = new HashSet<>();
            int i = 0;
            while (i < args.length) {
                switch (args[i]) {
                    case PORT_KEY:
                        if (portVisited) {
                            return Optional.empty();
                        }
                        if (args.length > i + 1 && args[i + 1].matches(PORT_PATTERN.pattern())) {
                            final var portCandidate = Integer.parseUnsignedInt(args[i + 1]);
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
                        i = fillNamesFromArgs(nameSet, args, i, STATUS_KEY);
                        nameVisited = true;
                        break;
                    case STATUS_KEY:
                        if (statusVisited) {
                            return Optional.empty();
                        }
                        i = fillNamesFromArgs(statusSet, args, i, NAME_KEY);
                        statusVisited = true;
                        break;
                }
            }
            if (!portVisited) {
                // port is mandatory
                return Optional.empty();
            }
            if (nameSet.isEmpty()) {
                // lists are optional, we can just provide defaults
                nameSet = Set.copyOf(DEFAULT_NAMES);
            }
            if (statusSet.isEmpty()) {
                statusSet = Set.copyOf(DEFAULT_STATUSES);
            }
            return Optional.of(new Config(port, nameSet, statusSet));
        }

        //fill set of names from args list; this parsing implementation is "tolerant" to tailing comma in the list or missing spaces between args. Not the most efficient.
        private static int fillNamesFromArgs(Set<String> setToFill, String[] args, int startIndex, String keyToAvoid) {
            int j = startIndex + 1;
            while (args.length > j && !(args[j].equals(PORT_KEY) || args[j].equals(keyToAvoid))) {
                final var stringToParse = args[j];
                if (stringToParse.contains(",")) {
                    setToFill.addAll(Arrays.stream(stringToParse.split(",")).filter(it -> !it.isEmpty()).collect(Collectors.toUnmodifiableList()));
                }
                setToFill.add(stringToParse);
                j++;
            }
            startIndex = j;
            return startIndex;
        }
    }
}
