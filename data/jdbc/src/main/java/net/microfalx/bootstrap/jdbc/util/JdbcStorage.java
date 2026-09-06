package net.microfalx.bootstrap.jdbc.util;

import net.microfalx.bootstrap.jdbc.support.Query;
import net.microfalx.bootstrap.jdbc.support.QueryProvider;
import net.microfalx.lang.UriUtils;
import net.microfalx.lang.annotation.SizeOf;
import net.microfalx.registry.Node;
import net.microfalx.registry.core.AbstractStorage;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.microfalx.registry.core.RegistryUtils.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class JdbcStorage extends AbstractStorage {

    private final Map<String, Long> nodeIds = new ConcurrentHashMap<>();
    @SizeOf private final QueryProvider queryProvider;

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public JdbcStorage(QueryProvider queryProvider) {
        this.queryProvider = queryProvider;
    }

    @Override
    public Collection<Node> getChildren(String path, boolean recursive) {
        String normalizedPath = normalizePath(path);
        Query query;
        if (recursive) {
            query = queryProvider.withResource("registry.get_children.sql").parameter(1, normalizedPath + "/%");
        } else {
            query = queryProvider.withResource("registry.get_direct_children.sql").parameter(1, getParentId(normalizedPath));
        }
        try {
            return query.selectMany((rs, rowNum) -> {
                AbstractStorage.StorageNode node = mapToStorageNode(rs);
                node.setExists(true);
                node.setLeaf(!hasChildren(node.getPath()));
                return node;
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<Node> getNode(String path) {
        String normalizedPath = normalizePath(path);
        Query query = queryProvider.withResource("registry.get_node.sql")
                .parameter(1, getNaturalId(normalizedPath));
        try {
            AbstractStorage.StorageNode node = query.selectOne((rs, rowNum) -> mapToStorageNode(rs));
            if (node != null) {
                node.setExists(true);
                node.setLeaf(!hasChildren(path));
            }
            return Optional.ofNullable(node);
        } catch (IncorrectResultSizeDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String path) {
        String normalizedPath = normalizePath(path);
        return getId(normalizedPath) != null;
    }

    @Override
    public byte[] get(String path) {
        String normalizedPath = normalizePath(path);
        Long id = getId(normalizedPath);
        if (id == null) return EMPTY_BYTE_ARRAY;
        Query query = queryProvider.withResource("registry.get_data.sql");
        query.parameter(1, id);
        try {
            return query.selectOne(String.class).getBytes();
        } catch (IncorrectResultSizeDataAccessException e) {
            return EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    public void put(String path, byte[] data) {
        put(path, data, 0);
    }

    @Override
    public void put(String path, byte[] data, int version) {
        String normalizedPath = normalizePath(path);
        Long parentId = createParentId(normalizedPath);
        createOrUpdateNode(parentId, normalizedPath, version);
        createOrUpdateData(getId(normalizedPath), data);
    }

    @Override
    public void remove(String path) {
        String normalizedPath = normalizePath(path);
        Long id = getId(normalizedPath);
        if (id != null) {
            queryProvider.withResource("registry.remove_data.sql").parameter(1, id).update();
            queryProvider.withResource("registry.remove_node.sql").parameter(1, id).update();
            nodeIds.remove(getNaturalId(normalizedPath));
        }
    }

    private AbstractStorage.StorageNode mapToStorageNode(ResultSet rs) throws SQLException {
        AbstractStorage.StorageNode node = new AbstractStorage.StorageNode(rs.getLong("id"),
                rs.getString("natural_id"), rs.getString("path"));
        node.setUpdateCount(rs.getInt("update_count"))
                .setVersion(rs.getInt("version"));
        node.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime())
                .setUpdatedAt(rs.getTimestamp("modified_at").toLocalDateTime());
        return node;
    }

    private boolean hasChildren(String path) {
        Long parentId = getParentId(path);
        if (parentId != null) {
            return queryProvider.withResource("registry.count_children.sql")
                    .parameter(1, parentId)
                    .selectInt() > 0;
        } else {
            return false;
        }
    }

    private Long getParentId(String path) {
        String parentPath = getParent(path);
        return getId(parentPath);
    }

    private Long createParentId(String path) {
        Long parentId = getParentId(path);
        if (parentId != null) return parentId;
        String parentPath = getParent(path);
        if (UriUtils.isRoot(path)) return null;
        Stack<String> paths = new Stack<>();
        while (!UriUtils.isRoot(parentPath)) {
            paths.push(parentPath);
            parentPath = getParent(parentPath);
        }
        Long lastParentId = null;
        while (!paths.empty()) {
            parentPath = paths.pop();
            parentId = getId(parentPath);
            if (parentId == null) createOrUpdateNode(lastParentId, parentPath, 1);
            lastParentId = getId(parentPath);
            if (lastParentId == null) {
                throw new IllegalStateException("Cannot find node id after creating node, path: " + path);
            }
        }
        return getParentId(path);
    }

    private Long getId(String path) {
        String naturalId = getNaturalId(path);
        Long id = nodeIds.get(naturalId);
        if (id == null) {
            try {
                id = queryProvider.withResource("registry.get_id.sql")
                        .parameter(1, naturalId)
                        .selectOne(Long.class);
            } catch (IncorrectResultSizeDataAccessException e) {
                // ignore
            }
        }
        if (id != null) nodeIds.put(naturalId, id);
        return id;
    }

    private void createOrUpdateNode(Long parentId, String path, int version) {
        LocalDateTime now = LocalDateTime.now();
        Query nodeQuery = queryProvider.withResource("registry.put_node.sql");
        nodeQuery.parameters(parentId, getNaturalId(path), path, now, now);
        nodeQuery.update();
    }

    private void createOrUpdateData(long id, byte[] data) {
        Query dataQuery = queryProvider.withResource("registry.put_data.sql");
        dataQuery.parameters(id, data);
        dataQuery.update();
    }
}
