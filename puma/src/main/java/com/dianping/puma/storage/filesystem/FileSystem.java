package com.dianping.puma.storage.filesystem;

import com.dianping.puma.storage.utils.DateUtils;
import com.dianping.puma.utils.PropertyKeyConstants;
import com.google.common.base.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public final class FileSystem {

    private static final String L1_INDEX_PREFIX = "l1Index";

    private static final String L1_INDEX_SUFFIX = ".l1idx";

    private static final String L2_INDEX_PREFIX = "bucket-";

    private static final String L2_INDEX_SUFFIX = ".l2idx";

    private static final String MASTER_DATA_PREFIX = "bucket-";

    private static final String MASTER_DATA_SUFFIX = ".data";

    private static final String SLAVE_DATA_PREFIX = "bucket-";

    private static final String SLAVE_DATA_SUFFIX = ".data";

    private static final String DEFAULT_PATH = "/data/appdatas/puma/";

    private static String l1IndexDir;

    private static String l2IndexDir;

    private static String masterDataDir;

    private static String slaveDataDir;

    static {
        String path = System.getProperty(PropertyKeyConstants.PUMA_STORAGE_PATH);

        if (Strings.isNullOrEmpty(path)) {
            path = DEFAULT_PATH;
        }

        l1IndexDir = path + "binlogIndex/l1Index/";
        l2IndexDir = path + "binlogIndex/l2Index/";
        masterDataDir = "storage/master/";
        slaveDataDir = "storage/slave/";
    }

    private FileSystem() {
    }

    public static String parseDb(File file) {
        return file.getParentFile().getParentFile().getName();
    }

    public static String parseDate(File file) {
        return file.getParentFile().getName();
    }

    public static int parseNumber(File file, String prefix, String suffix) {
        String numberString = StringUtils.substringBetween(file.getName(), prefix, suffix);
        return Integer.valueOf(numberString);
    }

    protected static int maxFileNumber(String baseDir, String database, String date, String prefix, String suffix) {
        File[] files = visitFiles(baseDir, database, date, prefix, suffix);
        int max = -1;
        for (File file : files) {
            int number = parseNumber(file, prefix, suffix);
            max = number > max ? number : max;
        }
        return max;
    }

    protected static File visitFile(
            String baseDir, String database, String date, int number, String prefix, String suffix) {
        File databaseDir = new File(baseDir, database);
        File dateDir = new File(databaseDir, date);
        File file = new File(dateDir, genFileName(number, prefix, suffix));

        return file.isFile() ? file : null;
    }

    protected static File[] visitFiles(
            String baseDir, String database, String date, final String prefix, final String suffix) {
        File databaseDir = new File(baseDir, database);
        File dateDir = new File(databaseDir, date);
        File[] files = dateDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith(prefix) && file.getName().endsWith(suffix);
            }
        });

        return files == null ? new File[0] : files;
    }

    public static File getL1IndexDir() {
        return new File(l1IndexDir);
    }

    public static File getL2IndexDir() {
        return new File(l2IndexDir);
    }

    public static File getMasterDataDir() {
        return new File(masterDataDir);
    }

    public static File getSlaveDataDir() {
        return new File(slaveDataDir);
    }

    public static File nextL1IndexFile(String database) throws IOException {
        File databaseDir = new File(l1IndexDir, database);
        File file = new File(databaseDir, L1_INDEX_PREFIX + L1_INDEX_SUFFIX);
        createFile(file);
        return file;
    }

    public static File visitL1IndexFile(String database) {
        File databaseDir = new File(l1IndexDir, database);
        File l1Index = new File(databaseDir, genL1IndexName());
        return l1Index.isFile() && l1Index.canRead() && l1Index.canWrite() ? l1Index : null;
    }

    public static File[] visitL2IndexDateDirs() {
        File[] dateDirs = new File[0];
        File[] databaseDirs = visitDatabaseDirs(l2IndexDir);
        for (File databaseDir : databaseDirs) {
            ArrayUtils.add(dateDirs, visitL2IndexDateDirs(databaseDir.getName()));
        }
        return dateDirs;
    }

    public static File[] visitL2IndexDateDirs(String database) {
        return visitDateDirs(l2IndexDir, database);
    }

    public static File visitL2IndexFile(String database, String date, int number) {
        return visitFile(l2IndexDir, database, date, number, L2_INDEX_PREFIX, L2_INDEX_SUFFIX);
    }

    public static File nextL2IndexFile(String database) throws IOException {
        int max = maxL2IndexFileNumber(database, today());
        return createFile(l2IndexDir, database, today(), max + 1, L2_INDEX_PREFIX, L2_INDEX_SUFFIX);
    }

    public static File[] visitMasterDataDateDirs() {
        File[] dateDirs = new File[0];
        File[] databaseDirs = visitDatabaseDirs(masterDataDir);
        for (File databaseDir : databaseDirs) {
            ArrayUtils.add(dateDirs, visitMasterDataDateDirs(databaseDir.getName()));
        }
        return dateDirs;
    }

    public static File[] visitMasterDataDateDirs(String database) {
        return visitDateDirs(masterDataDir, database);
    }

    public static File visitMasterDataFile(String database, String date, int number) {
        return visitFile(masterDataDir, database, date, number, MASTER_DATA_PREFIX, MASTER_DATA_SUFFIX);
    }

    public static File visitNextMasterDataFile(String database, String date, int number) {
        File file = visitMasterDataFile(database, date, number + 1);
        if (file != null) {
            return file;
        }

        while ((date = DateUtils.getNextDayWithoutFuture(date)) != null) {
            file = visitMasterDataFile(database, date, 0);
            if (file != null) {
                return file;
            }
        }

        return null;
    }

    public static File nextMasterDataFile(String database) throws IOException {
        int max = maxMasterFileNumber(database, today());
        return createFile(masterDataDir, database, today(), max + 1, MASTER_DATA_PREFIX, MASTER_DATA_SUFFIX);
    }

    public static File[] visitSlaveDataDateDirs() {
        File[] dateDirs = new File[0];
        File[] databaseDirs = visitDatabaseDirs(slaveDataDir);
        for (File databaseDir : databaseDirs) {
            ArrayUtils.add(dateDirs, visitSlaveDataDateDirs(databaseDir.getName()));
        }
        return dateDirs;
    }

    public static File[] visitSlaveDataDateDirs(String database) {
        return visitDateDirs(slaveDataDir, database);
    }

    public static File visitSlaveDataFile(String database, String date, int number) {
        return visitFile(slaveDataDir, database, date, number, SLAVE_DATA_PREFIX, SLAVE_DATA_SUFFIX);
    }

    public static File visitNextSlaveDataFile(String database, String date, int number) {
        File file = visitSlaveDataFile(database, date, number);
        if (file != null) {
            return file;
        }

        while ((date = DateUtils.getNextDayWithoutFuture(date)) != null) {
            file = visitSlaveDataFile(database, date, 0);
            if (file != null) {
                return file;
            }
        }

        return null;
    }

    public static File nextSlaveDataFile(String database) throws IOException {
        int max = maxSlaveFileNumber(database, today());
        return createFile(slaveDataDir, database, today(), max + 1, SLAVE_DATA_PREFIX, SLAVE_DATA_SUFFIX);
    }

    public static File mapSlaveDatabaseDir(File masterDatabaseDir) {
        String path = masterDatabaseDir.getAbsolutePath();
        String relative = StringUtils.substringAfter(path, masterDataDir);
        return new File(slaveDataDir, relative);
    }

    public static File mapSlaveDateDir(File masterDateDir) {
        return null;
    }

    public static File mapSlaveFile(File masterfile) {
        return null;
    }


    protected static String genL1IndexName() {
        return L1_INDEX_PREFIX + L1_INDEX_SUFFIX;
    }

    protected static String today() {
        return DateUtils.getNowString();
    }

    protected static File[] visitDatabaseDirs(String baseDir) {
        File[] files = new File(baseDir).listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }
        });

        return files == null ? new File[0] : files;
    }

    protected static File[] visitDateDirs(String baseDir, String database) {
        File databaseDir = new File(baseDir, database);
        File[] files = databaseDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }
        });

        return files == null ? new File[0] : files;
    }

    protected static String genFileName(int number, String prefix, String suffix) {
        return prefix + number + suffix;
    }

    protected static int maxL2IndexFileNumber(String database, String date) {
        return maxFileNumber(l2IndexDir, database, date, L2_INDEX_PREFIX, L2_INDEX_SUFFIX);
    }

    protected static int maxMasterFileNumber(String database, String date) {
        return maxFileNumber(masterDataDir, database, date, MASTER_DATA_PREFIX, MASTER_DATA_SUFFIX);
    }

    protected static int maxSlaveFileNumber(String database, String date) {
        return maxFileNumber(slaveDataDir, database, date, SLAVE_DATA_PREFIX, SLAVE_DATA_SUFFIX);
    }

    protected static void createFile(File file) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("failed to create file parent directories.");
        }

        if (!file.createNewFile()) {
            throw new IOException("file already exists.");
        }
    }

    protected static File createFile(String baseDir, String database, String date, int number, String prefix, String suffix) throws IOException {
        File databaseDir = new File(baseDir, database);
        File dateDir = new File(databaseDir, date);
        File file = new File(dateDir, genFileName(number, prefix, suffix));

        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("failed to create file parent directories.");
        }

        if (!file.createNewFile()) {
            throw new IOException("file already exists.");
        }

        return file;
    }

    public static String parseMasterDataDb(File file) {
        return parseDb(file);
    }

    public static String parseMasterDataDate(File file) {
        return parseDate(file);
    }

    public static int parseMasterDataNumber(File file) {
        return parseNumber(file, MASTER_DATA_PREFIX, MASTER_DATA_SUFFIX);
    }

}