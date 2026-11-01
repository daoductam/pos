package com.pos.identityaccess.infrastructure.persistence;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public TransactionRunner(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public <T> T inTransaction(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return transactionTemplate.execute(status -> action.get());
    }

    public void inTransaction(Runnable action) {
        Objects.requireNonNull(action, "action");
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
