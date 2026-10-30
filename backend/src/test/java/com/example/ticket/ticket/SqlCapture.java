package com.example.ticket.ticket;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/** Records the SQL actually prepared by MyBatis, after pagination rewriting. */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public class SqlCapture implements Interceptor {

    private final List<String> statements = new CopyOnWriteArrayList<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        statements.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
        return invocation.proceed();
    }

    public void clear() { statements.clear(); }
    public List<String> statements() { return List.copyOf(statements); }
}
