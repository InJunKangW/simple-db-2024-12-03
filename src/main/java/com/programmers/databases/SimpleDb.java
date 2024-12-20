package com.programmers.databases;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.Getter;
import lombok.Setter;

@Getter
public class SimpleDb {
    @Setter
    private boolean devMode = false;
    private final Queue<Connection> connectionPool = new ConcurrentLinkedDeque<>();

    @Getter
    private final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();

    public SimpleDb(String localhost, String id, String password, String database) {
        int connectionPoolSize = 100;
        try {
            for(int i = 0; i < connectionPoolSize; i++){
                connectionPool.add(DriverManager.getConnection(String.format("jdbc:mysql://%s:3306/%s", localhost, database), id, password));
            }
        }catch (SQLException e){
            throw new RuntimeException(e);
        }
    }

    private synchronized Connection getConnectionFromPool(){
        int chance = 0;
        while(connectionPool.isEmpty()){
            try {
                chance++;
                if(chance > 10){
                    throw new RuntimeException("Connection Time out");
                }
                Thread.sleep(500);
            }catch (InterruptedException e){
                throw new RuntimeException(e);
            }
        }
        return connectionPool.poll();
    }

    private Connection getConnection(){
        Connection connection = threadLocalConnection.get();
        if(connection == null){
            connection = getConnectionFromPool();
            threadLocalConnection.set(connection);
        }
        return connection;
    }

    public Sql genSql(){
        Connection connection = getConnection();
        return new Sql(this, connection);
    }


    public void run(Object... sql){
        Connection connection = getConnection();
        String query = String.valueOf(sql[0]);
        try (PreparedStatement preparedStatement = Objects.requireNonNull(connection).prepareStatement(query)){
            if(sql.length > 1){
                for (int i = 1; i < sql.length; i++) {
                    preparedStatement.setObject(i, sql[i]);
                }
            }
            preparedStatement.executeUpdate();
        }catch (SQLException e){
            throw new RuntimeException(e);
        }finally {
            connectionPool.offer(connection);
            threadLocalConnection.remove();
        }
    }


    public void startTransaction(){
        try{
            Connection connection = getConnection();
            connection.setAutoCommit(false);
        }catch (SQLException e){
            throw new RuntimeException(e);
        }
    }

    private Connection nullCheckedConnection(){
        Connection connection = threadLocalConnection.get();
        if(connection == null){
            throw new RuntimeException("Connection is null");
        }
        return connection;
    }
    public void closeConnection(){
        Connection connection = nullCheckedConnection();
        connectionPool.offer(connection);
        threadLocalConnection.remove();
    }

    public void rollback(){
        try(Connection connection = nullCheckedConnection()){
            connection.rollback();
            connection.setAutoCommit(true);
        }catch (SQLException e){
            throw new RuntimeException(e);
        }finally {
            threadLocalConnection.remove();
        }
    }

    public void commit(){
        try(Connection connection = nullCheckedConnection()){
            connection.commit();
            connection.setAutoCommit(true);
        }catch (SQLException e){
            throw new RuntimeException(e);
        }finally {
            threadLocalConnection.remove();
        }
    }
}
