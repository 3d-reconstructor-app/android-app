package com.example.a3dmodel.project;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.a3dmodel.App;
import com.example.a3dmodel.MainActivity;
import com.example.a3dmodel.TabPhoto;
import com.example.a3dmodel.Tab3DPlain;
import com.example.a3dmodel.TabMainMenu;
import com.example.a3dmodel.data.ProjectSnapshot;
import com.example.a3dmodel.exeption.AmbiguousProjectNameException;
import com.example.a3dmodel.exeption.ProjectException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProjectStorage implements Serializable {
    private final List<Project> projects;
    private final Map<String, Project> nameToProject;
    private Project currentProject;

    private ProjectStorage() {
        projects = new ArrayList<>();
        nameToProject = new HashMap<>();
    }

    private ProjectStorage(List<Project> projectList) throws ProjectException {
        this();
        projects.addAll(projectList);
        projectList.forEach(p -> nameToProject.put(p.getProjectName(), p));
    }

    private void setCurrentProject(Project proj) {
        setCurrentProject(proj, true);
    }

    private void setCurrentProject(Project proj, boolean notify) {
        currentProject = proj;
        if (notify) {
            Tab3DPlain.updateModelListAndSendItToAdapter();
            TabMainMenu.updateCurrentProject(proj);
        }
    }

    @NonNull
    public static ProjectStorage build() throws ProjectException {
        File resources = App.getContext().getFilesDir();
        List<Project> projects = new ArrayList<>();
        for (File projectFile : resources.listFiles()) {
            if (projectFile.isDirectory()) {
                continue;
            }
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(projectFile))) {
                Project proj = Project.deserialize(in);
                if (proj != null) {
                    projects.add(proj);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Unable to load project : " + projectFile);
                System.out.println(e.getMessage());
            }
        }
        ProjectStorage storage = new ProjectStorage(projects);
        storage.setCurrentProject(storage.getLastOrCreate(), false);
        return storage;
    }

    public void loadProject() throws ProjectException {
        loadProject(getCurrentProject());
    }

    private void loadProject(@NonNull Project project) throws ProjectException {
        openExistingProject(project.getProjectName());
    }

    public Project getCurrentProject() {
        assert (currentProject != null);
        return currentProject;
    }

    public List<ProjectSnapshot> getAllSnapshots() {
        return projects.stream().map(Project::makeSnapshot).collect(Collectors.toList());
    }

    public Project getLastOrCreate() throws ProjectException {
        if (projects.isEmpty()) {
            Project sampleProject = createNewProject("Sample Project", false);
            try {
                saveProject(sampleProject, false);
            } catch (ProjectException e) {
                Log.d(TAG, "Error while saving sample project");
            }
        }
        return projects.get(projects.size() - 1);
    }

    public void renameCurrentProject(String name) throws AmbiguousProjectNameException {
        if (nameToProject.containsKey(name)) {
            Log.d("ProjectStorage", "Project with given name already exists");
            throw new AmbiguousProjectNameException("Project with given name already exists");
        }
        nameToProject.remove(getCurrentProject().getProjectName());
        nameToProject.put(name, getCurrentProject());
        getCurrentProject().rename(name);
    }

    public Project createNewProject(String projectName) throws ProjectException {
        return createNewProject(projectName, true);
    }

    @NonNull
    private Project createNewProject(String projectName, boolean notifyAdapter) throws ProjectException {
        if (nameToProject.containsKey(projectName)) {
            Log.d("ProjectStorage", "Project with given name already exists");
            throw new ProjectException("Project with given name already exists");
        }
        Project newProject = Project.create(projectName);
        projects.add(newProject);
        nameToProject.put(projectName, newProject);
        if (notifyAdapter) {
            updateTabs();
        }
        return newProject;
    }

    public void openExistingProject(String projectName) throws ProjectException {
        if (!nameToProject.containsKey(projectName)) {
            throw new ProjectException("Project doesn't exist");
        }
        setCurrentProject(nameToProject.get(projectName));
        TabPhoto.loadImagesFromCurrentProject();
        Tab3DPlain.updateModelListAndSendItToAdapter();
    }

    public void saveProject() throws ProjectException {
        saveProject(getCurrentProject(), true);
    }

    private void saveProject(@NonNull Project projectToSave, boolean notify) throws ProjectException {
        String projectName = projectToSave.getProjectName();
        try (FileOutputStream fileOut = App.getContext().openFileOutput(projectName, Context.MODE_PRIVATE); ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            projectToSave.serialize(out);
        } catch (IOException e) {
            throw new ProjectException("Couldn't write project file for " + projectName);
        }
        if (notify) {
            updateTabs();
        }
    }

    public void deleteProjectByName(String projectName) throws IOException, ProjectException {
        Project projectToDelete = App.getProjectStorage().nameToProject.get(projectName);
        deleteProject(projectToDelete);
    }

    private void deleteProject(Project projectToDelete) throws IOException, ProjectException {
        projects.remove(projectToDelete);
        nameToProject.remove(projectToDelete.getProjectName());
        projectToDelete.clear();
        MainActivity.bitmapArrayList.clear();
        updateTabs();
        setCurrentProject(getLastOrCreate());
        TabPhoto.loadImagesFromCurrentProject();
        updateTabs();
        try {
            saveProject();
        } catch (ProjectException e) {
            Toast.makeText(App.getContext(), "Unable to save project", Toast.LENGTH_LONG).show();
        }
    }

    private void updateTabs() {
        TabMainMenu.updateProjectListAndSendItToAdapter();
        Tab3DPlain.updateModelListAndSendItToAdapter();
        TabPhoto.updateAllImagesAndSendItToAdapter();
    }
}
