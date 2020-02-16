package dev.chikanov.exercise;

public class NameCheckerImpl implements NameChecker {
    @Override
    public NameActor.NameStatus GetStatus() {
        return new NameActor.NameStatus("kek", "kek", 0);
    }
}
