package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.models.Skill;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the referenced {@code skills} catalog.
 * Students and drives copy {@code skillId} + a denormalized {@code skillName} into their embedded
 * arrays; {@link #updateSkill} propagates a rename to both with {@code updateMany} + arrayFilters.
 */
public class SkillDao {

    public static final String COLLECTION = "skills";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public List<Skill> findAllSkills(MongoDatabase db) {
        List<Skill> skills = new ArrayList<>();
        for (Document d : col(db).find().sort(Sorts.ascending("name"))) {
            skills.add(mapSkill(d));
        }
        return skills;
    }

    public Skill findById(MongoDatabase db, String skillId) {
        Document d = col(db).find(idFilter(skillId)).first();
        return d == null ? null : mapSkill(d);
    }

    public Skill findByName(MongoDatabase db, String name) {
        Document d = col(db).find(Filters.eq("name", nvl(name, ""))).collation(CASE_INSENSITIVE).first();
        return d == null ? null : mapSkill(d);
    }

    public boolean existsByName(MongoDatabase db, String name) {
        return col(db).find(Filters.eq("name", nvl(name, "")))
                .collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    public String insertSkill(MongoDatabase db, Skill skill) {
        Document d = toDocument(skill);
        col(db).insertOne(d);
        skill.setId(id(d));
        return skill.getId();
    }

    public void updateSkill(MongoDatabase db, Skill skill) {
        col(db).updateOne(idFilter(skill.getId()), Updates.combine(
                Updates.set("name", skill.getName()),
                Updates.set("category", skill.getCategory()),
                Updates.set("description", nvl(skill.getDescription(), "")),
                Updates.set("updatedAt", now())));

        // Keep denormalized copies consistent across the embedded arrays
        Object key = toKey(skill.getId());
        UpdateOptions opts = new UpdateOptions().arrayFilters(List.of(new Document("elem.skillId", key)));
        db.getCollection(StudentDao.COLLECTION).updateMany(
                Filters.eq("skills.skillId", key),
                Updates.set("skills.$[elem].skillName", skill.getName()), opts);
        db.getCollection(DriveDao.COLLECTION).updateMany(
                Filters.eq("requiredSkills.skillId", key),
                Updates.set("requiredSkills.$[elem].skillName", skill.getName()), opts);
    }

    public int countStudentReferences(MongoDatabase db, String skillId) {
        return (int) db.getCollection(StudentDao.COLLECTION).countDocuments(Filters.eq("skills.skillId", toKey(skillId)));
    }

    public int countDriveReferences(MongoDatabase db, String skillId) {
        return (int) db.getCollection(DriveDao.COLLECTION).countDocuments(Filters.eq("requiredSkills.skillId", toKey(skillId)));
    }

    public void deleteSkill(MongoDatabase db, String skillId) {
        col(db).deleteOne(idFilter(skillId));
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    public static Skill mapSkill(Document d) {
        return new Skill(id(d), str(d, "name"), str(d, "category"), str(d, "description"));
    }

    public static Document toDocument(Skill skill) {
        Document d = new Document();
        if (skill.getId() != null && !skill.getId().isBlank()) d.append("_id", toKey(skill.getId()));
        return d.append("name", skill.getName())
                .append("category", nvl(skill.getCategory(), "Technical Competency"))
                .append("description", nvl(skill.getDescription(), ""))
                .append("status", "ACTIVE")
                .append("createdAt", now());
    }
}
