package dev.chikanov.exercise;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

public class StatusSelectorImpl implements StatusSelector {
    private ArrayList<String> statuses;
    private Random random = new Random();

    public StatusSelectorImpl(Set<String> statuses) {
        this.statuses = new ArrayList<>(statuses);
    }

    @Override
    public String getRandomStatus() {
        return statuses.get(random.nextInt(statuses.size()));
    }
}
