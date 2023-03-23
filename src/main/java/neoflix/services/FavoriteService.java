package neoflix.services;

import neoflix.Params;
import neoflix.ValidationException;
import neoflix.Params.Sort;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.NoSuchRecordException;

import java.util.List;
import java.util.Map;

public class FavoriteService {
    private final Driver driver;

    /**
     * The constructor expects an instance of the Neo4j Driver, which will be
     * used to interact with Neo4j.
     *
     * @param driver
     */
    public FavoriteService(Driver driver) {
        this.driver = driver;
    }

    /**
     * This method should retrieve a list of movies that have an incoming
     * :HAS_FAVORITE
     * relationship from a User node with the supplied `userId`.
     *
     * Results should be ordered by the `sort` parameter, and in the direction
     * specified
     * in the `order` parameter.
     * Results should be limited to the number passed as `limit`.
     * The `skip` variable should be used to skip a certain number of rows.
     *
     * @param userId The unique ID of the user
     * @param params Query params for pagination and sorting
     * @return List<Movie> An list of Movie objects
     */
    // tag::all[]
    public List<Map<String, Object>> all(String userId, Params params) {
        // : Open a new session
        // : Retrieve a list of movies favorited by the user
        // : Close session
        try (var session = this.driver.session()) {
            var favorites = session.executeRead(transaction -> {
                String query = String.format("""
                        MATCH (u:User {userId: $userId})-[:HAS_FAVORITE]->(m:Movie)
                        RETURN m{.*, favorite: true} as movie
                        ORDER BY movie.`%s` %s
                        SKIP $skip
                        LIMIT $limit
                        """, params.sort(Sort.title), params.order());
                var result = transaction.run(query, Values.parameters("userId", userId, "skip", params.skip(),
                        "limit", params.limit()));
                return result.list(row -> row.get("movie").asMap());
            });
            return favorites;
        } catch (Neo4jException | NoSuchRecordException e) {
            // : handle exception
            throw new ValidationException("Could not retrieve favorites", Map.of("userId", userId));
        }
    }
    // end::all[]

    /**
     * This method should create a `:HAS_FAVORITE` relationship between
     * the User and Movie ID nodes provided.
     *
     * If either the user or movie cannot be found, a `NotFoundError` should be
     * thrown.
     *
     * @param userId  The unique ID for the User node
     * @param movieId The unique tmdbId for the Movie node
     * @return Map<String,Object></String,Object> The updated movie record with
     *         `favorite` set to true
     */
    // tag::add[]
    public Map<String, Object> add(String userId, String movieId) {
        // : Open a new Session
        // : Create HAS_FAVORITE relationship within a Write Transaction
        // : Close the session
        // : Return movie details and `favorite` property
        try (var session = this.driver.session()) {
            var favorite = session.executeWrite(transaction -> {
                String query = """
                        MATCH (u:User {userId: $userId})
                        MATCH (m:Movie {tmdbId: $movieId})
                        MERGE (u)-[r:HAS_FAVORITE]->(m)
                        ON CREATE SET r.createdAt = datetime()
                        RETURN m{.*, favorite: true} as movie
                        """;
                var result = transaction.run(query, Map.of("userId", userId, "movieId", movieId));
                return result.single().get("movie").asMap();
            });
            return favorite;
        } catch (NoSuchRecordException e) {
            throw new ValidationException(
                    String.format("Couldn't create a favorite relationship for User %s and Movie %s", userId, movieId),
                    Map.of("movie", movieId, "user", userId));
        }
    }
    // end::add[]

    /*
     * This method should remove the `:HAS_FAVORITE` relationship between
     * the User and Movie ID nodes provided.
     * If either the user, movie or the relationship between them cannot be found,
     * a `NotFoundError` should be thrown.
     * 
     * @param userId The unique ID for the User node
     * 
     * @param movieId The unique tmdbId for the Movie node
     * 
     * @return Map<String,Object></String,Object> The updated movie record with
     * `favorite` set to true
     */
    // tag::remove[]
    public Map<String, Object> remove(String userId, String movieId) {
        // : Open a new Session
        // : Delete the HAS_FAVORITE relationship within a Write Transaction
        // : Close the session
        // : Return movie details and `favorite` property
        try (var session = this.driver.session()) {
            var favorite = session.executeWrite(transaction -> {
                String query = """
                        MATCH (u:User {userId: $userId})-[r:HAS_FAVORITE]->(m:Movie {tmdbId: $movieId})
                        DELETE r
                        RETURN m{.*, favorite: false} as movie
                        """;
                var result = transaction.run(query, Map.of("userId", userId, "movieId", movieId));
                return result.single().get("movie").asMap();
            });
            return favorite;
        } catch (NoSuchRecordException e) {
            throw new ValidationException(
                    String.format("Couldn't delete a favorite relationship for User %s and Movie %s", userId, movieId),
                    Map.of("movie", movieId, "user", userId));

        }
    }
    // end::remove[]

}
