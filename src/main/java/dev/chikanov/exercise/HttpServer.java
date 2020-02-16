package dev.chikanov.exercise;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class HttpServer extends AllDirectives {
    public static void main(String[] args) throws IOException {
        ActorSystem system = ActorSystem.create("routes");

        starHttpServer(system);

    }

    private static void starHttpServer(ActorSystem system) throws IOException {
        final Http http = Http.get(system);

        //In order to access all directives we need an instance where the routes are define.
        HttpServer app = new HttpServer();

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow(system, Materializer.matFromSystem(system));
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost("localhost", 8080), Materializer.matFromSystem(system));

        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read(); // let it run until user presses return
        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }

    interface Command {
    }

    public final static class Dummy implements Command {
        public final ActorRef replyTo;
        public final String val = "someVal";

        public Dummy(ActorRef replyTo) {
            this.replyTo = replyTo;
        }
    }

    private Route createRoute() {
        return pathPrefix("check", () -> path(PathMatchers.segment(), (String name) -> get(() ->
                rejectEmptyResponse(() -> onSuccess(, performed -> complete("NaN"))))));
    }

    private static final class Config {
        public final int listenPort;
        public final Set<String> names;
        public final Set<String> statuses;

        public static final Set<String> defaultNames = Set.of("John", "Alex", "Juan", "Alejandro");
        public static final Set<String> defaultStatuses = Set.of("asleep", "awake");

        private Config(int listenPort, Set<String> names, Set<String> statuses) {
            this.listenPort = listenPort;
            this.names = names;
            this.statuses = statuses;
        }
    }
}
