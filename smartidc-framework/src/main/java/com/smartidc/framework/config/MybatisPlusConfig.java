package com.smartidc.framework.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 核心拦截器配置 (多租户强隔离、机房行级数据权限与分页插件)
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 不需要进行多租户 SQL 拦截过滤的表集合 (如按天分区的时序快照表)
     */
    private static final Set<String> IGNORE_TENANT_TABLES = Set.of(
            "idc_telemetry_snapshot",
            "sys_user"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户数据强隔离拦截器
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 从当前线程上下文中获取租户号 (默认 000000)
                return new StringValue(TenantContext.getTenantId());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TENANT_TABLES.contains(tableName.toLowerCase());
            }
        }));

        // 2. 机房行级数据权限切面拦截器 (T1.2: 运维工程师按管辖机房过滤)
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionHandler() {
            @Override
            public Expression getSqlSegment(Expression where, String mappedStatementId) {
                // 若当前登录者是现场运维工程师 (roleKey == "engineer")
                if (UserContext.isEngineer()) {
                    Set<String> rooms = UserContext.getAssignedRooms();
                    if (rooms == null || rooms.isEmpty()) {
                        // 未指派任何机房，返回 1 = 0 阻断越权查询
                        try {
                            Expression zeroExpr = CCJSqlParserUtil.parseCondExpression("1 = 0");
                            return where == null ? zeroExpr : new AndExpression(where, zeroExpr);
                        } catch (Exception e) {
                            return null;
                        }
                    }

                    // 针对机架资产相关的 Mapper 查询自动注入机房范围
                    if (mappedStatementId.contains("IdcRackMapper")) {
                        String inClause = rooms.stream()
                                .map(r -> "'" + r.replace("'", "''") + "'")
                                .collect(Collectors.joining(", ", "room_name IN (", ")"));
                        try {
                            Expression roomExpr = CCJSqlParserUtil.parseCondExpression(inClause);
                            return where == null ? roomExpr : new AndExpression(where, roomExpr);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
                return null;
            }
        }));

        // 3. 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        return interceptor;
    }
}
