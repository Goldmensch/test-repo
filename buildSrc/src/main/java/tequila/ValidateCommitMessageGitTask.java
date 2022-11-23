package tequila;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ValidateCommitMessageGitTask extends DefaultTask {

    public static final Collection<String> TEMPLATE_PROJECT_SCOPES = List.of("gradle", "readme", "git", "workflows");

    public static final String TEMPLATE_PROJECT_ORIGIN = "Goldmensch/tequila-java-gradle-template.git";

    public static final Pattern HEADER_PATTERN =
            Pattern.compile("^(?<type>\\w+?)(?:\\((?<scope>\\w+?)\\))?!?: (?<message>\\S[^.]*)");

    public static final Pattern MERGE_PATTERN =
            Pattern.compile("Merge .*");

    protected final Repository repository = new RepositoryBuilder()
            .setGitDir(new File(getProject().getRootDir(), ".git"))
            .readEnvironment()
            .build();

    protected final Git git = new Git(repository);

    protected final Collection<String> types = parseList(getProject().property("commit.types"));

    protected final Collection<String> scopes = isTemplateRepository(repository)
            ? parseList(getProject().property("commit.scopes"))
            : TEMPLATE_PROJECT_SCOPES;

    public ValidateCommitMessageGitTask() throws Exception {
    }

    @TaskAction
    public void task() throws Exception {
        var message = ((String) getProject().property("message"));
        if (message == null) {
            throw new IllegalArgumentException("Message must be non null");
        }

        var errors = new ArrayList<String>();
        var header = message.lines().findFirst().orElseThrow();
        errors.addAll(validateHeader(header));
        var footer = message.lines().skip(1).collect(Collectors.joining("\n"));
        errors.addAll(validateFooter(footer));
        errors.forEach(s -> System.out.println("Error: %s".formatted(s))); // dirty gradle bash hack
    }

    public void addErr(String err) {
        getState().addFailure(new TaskExecutionException(this, new RuntimeException(err)));
    }

    protected Collection<String> validateFooter(String fullMessage) {
        // TODO: 14.11.22 Implement validation for footer
        return List.of();
    }

    protected Collection<String> validateHeader(String header) {
        var matcher = HEADER_PATTERN.matcher(header);
        var errors = new ArrayList<String>();
        if (matcher.matches()) {
            var type = matcher.group("type");
            var scope = matcher.group("scope");
            if (!types.contains(type)) {
                errors.add("-> Unkown type '%header'".formatted());
            }
            if (scope != null && !scopes.contains(scope)) {
                errors.add("-> Unkown scope '%header'".formatted(scope));
            }
        } else {
            errors.add("Commit header (short commit message) violates conventional commits format.");
        }
        return errors;
    }

    private boolean isTemplateRepository(Repository repository) {
        return repository.getConfig().getString("remote", "origin", "url")
                .contains(TEMPLATE_PROJECT_ORIGIN);
    }

    // assume validated branch naming
    public String rootBranch(String branch) {
        var branches = branch.split("/");
        return switch (branches.length) {
            case 1, 2 -> "main";
            case 3 -> branches[1];
            default -> throw new IllegalArgumentException("Branch doens't follow branch naming conventions.");

        };
    }

    protected Collection<String> parseList(Object source) {
        return Arrays.stream(((String) source).split(",")).map(String::trim)
                .toList();
    }
}
