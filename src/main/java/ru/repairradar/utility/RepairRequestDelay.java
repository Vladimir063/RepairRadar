package ru.repairradar.utility;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RepairRequestDelay {

    public void pause() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3001));
    }
}
