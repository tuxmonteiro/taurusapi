package com.globo.ateam.taurusapi.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;

@Service
public class TaskRunnerService {

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", "/tmp");
    private static final String BZT_CMD = System.getProperty("bzt.cmd", "/usr/local/bin/bzt");
    private static final char   BRK_LN = (char) 0x0a;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ConcurrentLinkedQueue<Callable<Result>> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String taurusTmpDir = TMP_DIR + "/taurusconfs";

    public TaskRunnerService() throws IOException {
        log.info("Using " + taurusTmpDir + " as tmpdir");
        if (!Files.exists(Paths.get(taurusTmpDir))) Files.createDirectory(Paths.get(taurusTmpDir));
    }

    public String getTaurusTmpDir() {
        return taurusTmpDir;
    }

    public void put(long testId, String body) {
        queue.add(() -> {
            log.info("executing task id " + testId);
            try {
                String idDir = taurusTmpDir + "/" + testId;
                if (!Files.exists(Paths.get(idDir))) Files.createDirectory(Paths.get(idDir));
                String confFile = idDir + "/test.json";
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(confFile))) {
                    writer.write(body);
                }
                Process process = new ProcessBuilder(BZT_CMD, "-o", "settings.artifacts-dir=" + idDir + "/artifacts-dir", confFile).start();
                InputStream is = process.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(is);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    result.append(line);
                    result.append(BRK_LN);
                }
                return new Result(testId,result.toString());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            return new Result(-1L, "");
        });
        log.info("added task id " + testId);
    }

    @Scheduled(fixedDelay = 5000)
    public void run() throws ExecutionException, InterruptedException, IOException {
        if (!queue.isEmpty()) {
            final Callable<Result> task = queue.poll();
            final Result result = executor.submit(task).get();
            long id = result.getId();
            String resultOutputFile = taurusTmpDir + "/" + id + "/result";
            String resultOutput = result.getResult();
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(resultOutputFile))) {
                writer.write(resultOutput);
            }
            log.info("task id " + id + " executed");
        }
    }

    private static class Result {

        private final long id;
        private final String result;

        Result(long id, String result) {
            this.id = id;
            this.result = result;
        }

        long getId() {
            return id;
        }

        String getResult() {
            return result;
        }
    }
}
