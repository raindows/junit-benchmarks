package com.carrotsearch.junitbenchmarks.h2;

import java.io.*;
import java.sql.*;

import org.h2.jdbcx.JdbcDataSource;

import com.carrotsearch.junitbenchmarks.*;

/**
 * {@link IResultsConsumer} that appends records to a H2 database.
 */
public final class H2Consumer extends AutocloseConsumer implements Closeable
{
    /*
     * Column indexes in the prepared insert statement.
     */
    private final static int RUN_ID, CLASSNAME, NAME, BENCHMARK_ROUNDS, WARMUP_ROUNDS,
        ROUND_AVG, ROUND_STDDEV,
        GC_AVG, GC_STDDEV,
        GC_INVOCATIONS, GC_TIME,
        TIME_BENCHMARK, TIME_WARMUP;

    static
    {
        int column = 1;
        RUN_ID = column++;
        CLASSNAME = column++;
        NAME = column++;
        BENCHMARK_ROUNDS = column++;
        WARMUP_ROUNDS = column++;
        ROUND_AVG = column++;
        ROUND_STDDEV = column++;
        GC_AVG = column++;
        GC_STDDEV = column++;
        GC_INVOCATIONS = column++;
        GC_TIME = column++;
        TIME_BENCHMARK = column++;
        TIME_WARMUP = column++;
    }

    /* */
    private Connection connection;

    /** Unique primary key for this consumer in the RUNS table. */
    private int runId;

    /** Insert statement to the tests table. */
    private PreparedStatement newTest;

    /*
     *
     */
    public H2Consumer(File dbFileName)
    {
        try
        {
            final JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
    
            ds.setURL("jdbc:h2:" + dbFileName.getAbsolutePath());
            ds.setUser("sa");
    
            this.connection = ds.getConnection();
            connection.setAutoCommit(false);
    
            checkSchema();
    
            runId = getRunID();
    
            newTest = connection.prepareStatement(getSQL("003-new-result.sql"));
            newTest.setInt(RUN_ID, runId);
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Cannot initialize H2 database.", e);
        }
    }

    /**
     * @return Create a row for this consumer's test run.
     */
    private int getRunID() throws SQLException
    {
        PreparedStatement s = connection.prepareStatement(
            getSQL("002-new-run.sql"), Statement.RETURN_GENERATED_KEYS);
        s.setString(1, System.getProperty("os.arch", "?"));
        s.setString(2, System.getProperty("java.runtime.version", "?"));
        s.executeUpdate();
        
        ResultSet rs = s.getGeneratedKeys();
        if (!rs.next()) throw new SQLException("No autogenerated keys?");
        final int key = rs.getInt(1);
        if (rs.next()) throw new SQLException("More than one autogenerated key?");

        connection.commit();
        rs.close();
        s.close();

        return key;
    }

    /**
     * Accept a single benchmark result.
     */
    public void accept(Result result)
    {
        try
        {
            newTest.setString(CLASSNAME, result.target.getClass().getSimpleName());
            newTest.setString(NAME, result.method.getName());

            newTest.setInt(BENCHMARK_ROUNDS, result.benchmarkRounds);
            newTest.setInt(WARMUP_ROUNDS, result.warmupRounds);

            newTest.setDouble(ROUND_AVG, result.roundAverage.avg);
            newTest.setDouble(ROUND_STDDEV, result.roundAverage.stddev);

            newTest.setDouble(GC_AVG, result.gcAverage.avg);
            newTest.setDouble(GC_STDDEV, result.gcAverage.stddev);

            newTest.setInt(GC_INVOCATIONS, (int) result.gcInfo.accumulatedInvocations());
            newTest.setDouble(GC_TIME, result.gcInfo.accumulatedTime() / 1000.0);

            newTest.setDouble(TIME_BENCHMARK, result.warmupTime / 1000);
            newTest.setDouble(TIME_WARMUP, result.benchmarkTime / 1000);

            newTest.executeUpdate();
            connection.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(
                "Error while saving the benchmark result to H2.", e);
        }
    }

    /**
     * Close the output XML stream.
     */
    public void close()
    {
        try
        {
            if (connection != null)
            {
                if (!connection.isClosed())
                    connection.close();
                connection = null;
            }
        }
        catch (SQLException e)
        {
            // Ignore?
        }
    }

    /**
     * Check database schema and create it if needed.
     */
    private void checkSchema() throws SQLException
    {
        final Statement s = connection.createStatement();

        s.execute(getSQL("000-create-runs.sql"));
        s.execute(getSQL("001-create-tests.sql"));
        connection.commit();
        s.close();
    }

    /**
     * Read a given resource from classpath and return UTF-8 decoded string.
     */
    private String getSQL(String resourceName)
    {
        try
        {
            InputStream is = this.getClass().getResourceAsStream(resourceName);
            if (is == null) throw new IOException();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte [] buffer = new byte [1024];
            int cnt;
            while ((cnt = is.read(buffer)) > 0) {
                baos.write(buffer, 0, cnt);
            }
            is.close();
            baos.close();

            return new String(baos.toByteArray(), "UTF-8");
        }
        catch (IOException e)
        {
            throw new RuntimeException("Required resource missing: "
                + resourceName);
        }
    }
}
