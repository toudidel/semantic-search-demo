package pl.example.semanticsearch.article;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArticleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String title, String content, String category, float[] embedding) {
        return jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO article(title, content, category, embedding) VALUES (?, ?, ?, ?) RETURNING id")) {
                statement.setString(1, title);
                statement.setString(2, content);
                statement.setString(3, category);
                statement.setObject(4, new PGvector(embedding));
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    public int insertBatch(Collection<ArticleDraft> articles) {
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO article(title, content, category, embedding) VALUES (?, ?, ?, ?)")) {
                for (var article : articles) {
                    statement.setString(1, article.title());
                    statement.setString(2, article.content());
                    statement.setString(3, article.category());
                    statement.setObject(4, new PGvector(article.embedding()));
                    statement.addBatch();
                }
                return java.util.Arrays.stream(statement.executeBatch()).sum();
            }
        });
    }

    public List<Article> search(float[] queryVector, String category, int limit) {
        return jdbcTemplate.execute((ConnectionCallback<List<Article>>) connection -> {
            PGvector.registerTypes(connection);
            var sql = """
                    SELECT id, title, content, category,
                           embedding <=> ? AS distance, created_at
                    FROM article
                    WHERE (? IS NULL OR category = ?)
                    ORDER BY embedding <=> ?
                    LIMIT ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                var vector = new PGvector(queryVector);
                statement.setObject(1, vector);
                statement.setString(2, category);
                statement.setString(3, category);
                statement.setObject(4, vector);
                statement.setInt(5, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    var results = new java.util.ArrayList<Article>();
                    while (resultSet.next()) {
                        results.add(new Article(
                                resultSet.getLong("id"),
                                resultSet.getString("title"),
                                resultSet.getString("content"),
                                resultSet.getString("category"),
                                resultSet.getFloat("distance"),
                                resultSet.getObject("created_at", java.time.OffsetDateTime.class)));
                    }
                    return results;
                }
            }
        });
    }

    public record ArticleDraft(String title, String content, String category, float[] embedding) {
    }
}
