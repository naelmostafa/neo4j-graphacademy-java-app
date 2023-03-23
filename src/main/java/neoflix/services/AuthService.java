package neoflix.services;

import neoflix.AppUtils;
import neoflix.AuthUtils;
import neoflix.ValidationException;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.NoSuchRecordException;

import java.util.List;
import java.util.Map;

public class AuthService {

    private final Driver driver;
    private final List<Map<String, Object>> users;
    private String jwtSecret;

    /**
     * The constructor expects an instance of the Neo4j Driver, which will be
     * used to interact with Neo4j.
     *
     * @param driver
     * @param jwtSecret
     */
    public AuthService(Driver driver, String jwtSecret) {
        this.driver = driver;
        this.jwtSecret = jwtSecret;
        this.users = AppUtils.loadFixtureList("users");
    }

    /**
     * This method should create a new User node in the database with the email and
     * name
     * provided, along with an encrypted version of the password and a `userId`
     * property
     * generated by the server.
     *
     * The properties also be used to generate a JWT `token` which should be
     * included
     * with the returned user.
     *
     * @param email
     * @param plainPassword
     * @param name
     * @return User
     */
    // tag::register[]
    public Map<String, Object> register(String email, String plainPassword, String name) {
        var encrypted = AuthUtils.encryptPassword(plainPassword);
        // Open Session
        try (var session = this.driver.session()) {
            var user = session.executeWrite(transaction -> {
                String query = """
                        CREATE (u:User {
                            userId: randomUUID(),
                            name: $name,
                            email: $email,
                            password: $encrypted
                        })
                        RETURN u{.userId, .name, .email} as user """;
                var result = transaction.run(query,
                        Values.parameters("name", name, "email", email, "encrypted", encrypted));
                return result.single().get("user").asMap();
            });
            String subject = user.get("userId").toString();
            String token = AuthUtils.sign(subject, userToClaims(user), jwtSecret);
            return userWithToken(user, token);
        } catch (ClientException e) {
            if (e.code().equals("Neo.ClientError.Schema.ConstraintValidationFailed")) {
                throw new ValidationException("An account already exists with the email address",
                        Map.of("email", "Email address already taken"));
            }
            throw e;
        }
    }
    // end::register[]

    /**
     * This method should attempt to find a user by the email address provided
     * and attempt to verify the password.
     *
     * If a user is not found or the passwords do not match, a `false` value should
     * be returned. Otherwise, the users properties should be returned along with
     * an encoded JWT token with a set of 'claims'.
     *
     * {
     * userId: 'some-random-uuid',
     * email: 'graphacademy@neo4j.com',
     * name: 'GraphAcademy User',
     * token: '...'
     * }
     *
     * @param email         The user's email address
     * @param plainPassword An attempt at the user's password in unencrypted form
     * @return User Resolves to a null value when the user is not found or password
     *         is incorrect.
     */
    // tag::authenticate[]
    public Map<String, Object> authenticate(String email, String plainPassword) {
        // TODO: Authenticate the user from the database
        try (var session = this.driver.session()) {
            var user = session.executeRead(transaction -> {
                String query = """
                        MATCH (u:User {email: $email})
                        RETURN u{.userId, .name, .email, .password} as user
                        """;
                var result = transaction.run(query, Values.parameters("email", email));
                return result.single().get("user").asMap();
            });
            var encrypted = user.get("password").toString();
            if (!AuthUtils.verifyPassword(plainPassword, encrypted)) {
                throw new ValidationException("Incorrect password",
                        Map.of("email", "Incorrect email"));
            }
            // tag::return[]
            String sub = (String) user.get("userId");
            String token = AuthUtils.sign(sub, userToClaims(user), jwtSecret);
            return userWithToken(user, token);
            // end::return[]
        } catch (NoSuchRecordException e) {
            throw new ValidationException("Incorrect email", Map.of("email", "Incorrect email"));
        }
    }
    // end::authenticate[]

    private Map<String, Object> userToClaims(Map<String, Object> user) {
        return Map.of(
                "sub", user.get("userId"),
                "userId", user.get("userId"),
                "name", user.get("name"));
    }

    private Map<String, Object> claimsToUser(Map<String, String> claims) {
        return Map.of(
                "userId", claims.get("sub"),
                "name", claims.get("name"));
    }

    private Map<String, Object> userWithToken(Map<String, Object> user, String token) {
        return Map.of(
                "token", token,
                "userId", user.get("userId"),
                "email", user.get("email"),
                "name", user.get("name"));
    }
}
