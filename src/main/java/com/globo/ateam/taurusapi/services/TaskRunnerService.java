package com.globo.ateam.taurusapi.services;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;

import static com.globo.ateam.taurusapi.services.TaskRunnerService.Mapper.JSON_MAPPER;
import static com.globo.ateam.taurusapi.services.TaskRunnerService.Mapper.YAML_MAPPER;

@SuppressWarnings("FieldCanBeLocal")
@Service
public class TaskRunnerService {

    enum Mapper {
        YAML_MAPPER(new YAMLFactory()),
        JSON_MAPPER(new JsonFactory());

        public ObjectMapper value() {
            return mapper;
        }

        private final ObjectMapper mapper;
        Mapper(JsonFactory factory) {
            mapper = new ObjectMapper(factory);
        }
    }

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", "/tmp");
    private static final String BZT_CMD = System.getProperty("bzt.cmd", "/usr/local/bin/bzt");
    private static final char   BRK_LN = (char) 0x0a;

    private static final Integer TASK_LIMIT = Integer.parseInt(System.getProperty("task.limit", "0"));

    private final String errorQueueOverflowMessage = "ERROR: Task Queue Overflow. Try again later";
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ConcurrentLinkedQueue<Callable<Result>> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String taurusTmpDir = TMP_DIR + "/taurusconfs";

    public TaskRunnerService() throws IOException {
        log.info("Using " + taurusTmpDir + " as tmpdir");
        createDir(taurusTmpDir);
    }

    public String getTaurusTmpDir() {
        return taurusTmpDir;
    }

    public void put(long testId, byte[] body, String contentType) {
        if (taskQueueOverflow(testId)) return;
        queue.add(() -> {
            log.info("executing task id " + testId);
            try {
                final String mediaType = extractMediaType(contentType);
                final byte[] confBody = buildConf(body, mediaType);
                final String idDir = extractIdDirectory(testId);
                final String confFile = idDir + "/test." + mediaType;
                writeToFile(confBody, confFile);
                return new Result(testId, extractResult(runTaurus(confFile, idDir)));
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            return new Result(-1L, "");
        });
        log.info("added task id " + testId);
    }

    private String extractIdDirectory(long testId) {
        return taurusTmpDir + "/" + testId;
    }

    private String extractResultFile(long testId) {
        return extractIdDirectory(testId) + "/result";
    }

    private boolean taskQueueOverflow(long testId) {
        try {
            createDir(extractIdDirectory(testId));
            if (TASK_LIMIT != 0 && queue.size() >= TASK_LIMIT) {
                String resultFile = extractResultFile(testId);
                writeToFile(("{\"status\":\"" + errorQueueOverflowMessage + "\"}").getBytes(Charset.defaultCharset()), resultFile);
                log.error(errorQueueOverflowMessage);
                log.warn("task id " + testId + " NOT executed");
                return true;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return true;
        }
        return false;
    }

    private void createDir(String idDir) throws IOException {
        if (!Files.exists(Paths.get(idDir))) Files.createDirectory(Paths.get(idDir));
    }

    private String extractMediaType(String contentType) {
        return MediaType.APPLICATION_JSON_VALUE.toUpperCase().contains(contentType.toUpperCase()) ? "json" : "yml";
    }

    private void writeToFile(byte[] tree, String confFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(confFile)) {
            fos.write(tree);
        }
    }

    private String extractResult(BufferedReader bufferedReader) throws IOException {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            result.append(line).append(BRK_LN);
        }
        return result.toString();
    }

    private BufferedReader runTaurus(String confFile, String idDir) throws IOException {
        String artifactsDir = idDir + "/artifacts-dir";
        createDir(artifactsDir);
        Process process = new ProcessBuilder(BZT_CMD, "-v", "-o", "settings.artifacts-dir=" + artifactsDir, confFile).start();
        InputStream is = process.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(is);
        return new BufferedReader(inputStreamReader);
    }

    private byte[] buildConf(byte[] body, String mediaType) throws IOException {
        final ObjectMapper mapper = ("yml".equals(mediaType)) ? YAML_MAPPER.value() : JSON_MAPPER.value();
        ObjectNode root = (ObjectNode) mapper.readTree(body);
        ObjectNode settings = mapper.createObjectNode();
        settings.put("check-updates", false);
        settings.put("check-interval", "10s");
        root.set("settings", settings);
        ObjectNode modules = mapper.createObjectNode();
        ObjectNode consoleModule = mapper.createObjectNode();
        ObjectNode finalStatsModule = mapper.createObjectNode();
        ObjectNode functionalConsolidatorModule = mapper.createObjectNode();
        consoleModule.put("disable", true);
        finalStatsModule.put("disable", true);
        functionalConsolidatorModule.put("disable", true);
        modules.set("console", consoleModule);
        modules.set("final-stats", finalStatsModule);
        modules.set("functional-consolidator", functionalConsolidatorModule);
        root.set("modules", modules);
        root.put("provisioning", "local");
        return mapper.writeValueAsBytes(root);
    }

    @Scheduled(fixedDelay = 5000)
    public void run() throws ExecutionException, InterruptedException, IOException {
        if (!queue.isEmpty()) {
            final Callable<Result> task = queue.poll();
            final Result result = executor.submit(task).get();
            long id = result.getId();
            String resultFile = extractResultFile(id);
            String resultBody = result.getResult();
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(resultFile))) {
                writer.write(resultBody);
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
