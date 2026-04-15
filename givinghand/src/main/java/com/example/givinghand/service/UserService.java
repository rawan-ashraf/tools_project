package com.example.givinghand.service;
import com.example.givinghand.entity.user;
import com.example.givinghand.dto.Request;
import com.example.givinghand.util.Validation;
import com.example.givinghand.dto.login;
import com.example.givinghand.dto.update;
import java.sql.Date;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
@Stateless
public class UserService {
    @PersistenceContext
    private EntityManager em;
    public String register(Request req) {

        if (req.email == null || req.password == null || req.name == null || req.role == null) {
            return "Missing required fields";
        }
        if (!Validation.isValidEmail(req.email))
            return "Invalid email";

        if (!Validation.isValidBirthday(req.birthday))
            return "Invalid birthday";
        user user = new user();
        user.setEmail(req.email);
        user.setPassword(req.password);
        user.setName(req.name);
        user.setBirthday(Date.valueOf(req.birthday));
        user.setBio(req.bio);
        user.setRole(req.role.toLowerCase());

        em.persist(user);

        return "User registered successfully.";
    }
    public String login(login req) {

        if (req.email == null || req.password == null)
            return "Missing required fields";

        user user = em.createQuery(
                        "SELECT u FROM user u WHERE u.email = :email",
                        user.class
                )
                .setParameter("email", req.email)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (user == null)
            return "Invalid email";

        if (!user.getPassword().equals(req.password))
            return "Invalid password";

        return "Login successful";
    }
    public String updateProfile(update req) {

        if (req.name == null || req.name.isBlank())
            return "Missing or invalid name";

        user user = em.find(user.class, req.userId);

        if (user == null)
            return "User not found";

        user.setName(req.name);
        user.setBio(req.bio);

        em.merge(user);

        return "Profile updated successfully";
    }
    public user findUserById(int id) {
        return em.find(user.class, id);
    }

}
