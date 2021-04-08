package com.rawcopier;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FileUtils;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RawCopier
{
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  public static final String FILE_EXTENSION = "cr2";

  @FXML
  private GridPane pane;
  @FXML
  private Button copy;
  @FXML
  private Label sourceFolderLabel;
  @FXML
  private Label targetFolderLabel;
  @FXML
  private ProgressBar progressBar;

  private File sourceDir;
  private File targetDir;

  private long filesCount;
  private long copied = 0;

  public void chooseSource()
  {
    sourceDir = choose(sourceFolderLabel);
    copy.setDisable(sourceDir == null || targetDir == null);
  }

  public void chooseTarget()
  {
    targetDir = choose(targetFolderLabel);
    copy.setDisable(sourceDir == null || targetDir == null);
  }

  private File choose(Label label)
  {
    DirectoryChooser directoryChooser = new DirectoryChooser();
    File dir = directoryChooser.showDialog(pane.getScene().getWindow());
    if (dir != null)
    {
      label.setText(dir.getAbsolutePath());
    }
    return dir;
  }

  private List<File> readFiles()
  {
    File[] files = sourceDir.listFiles(name -> FILE_EXTENSION.equalsIgnoreCase(FilenameUtils.getExtension(name.getName())));
    return Optional.ofNullable(files)
      .map(Arrays::asList)
      .orElseGet(ArrayList::new);
  }

  public void copy()
  {
    filesCount = 0;
    copied = 0;

    List<File> files = readFiles();
    filesCount = files.size();

    Task<Void> task = new Task<>()
    {
      @Override
      protected Void call()
      {
        files.stream()
          .collect(Collectors.groupingBy(RawCopier::getLastModificationDate))
          .forEach(this::copy);
        return null;
      }

      private void copy(String dirName, Collection<File> files)
      {
        File directory = new File(targetDir, dirName);
        try
        {
          File target = directory.exists() ? directory : Files.createDirectory(directory.toPath()).toFile();
          files.forEach(file -> {
            try
            {
              FileUtils.copyFileToDirectory(file, target);
              updateProgress(++copied, filesCount);
            } catch (IOException e)
            {
              e.printStackTrace();
            }
          });
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
    };

    progressBar.progressProperty().bind(task.progressProperty());

    Thread thread = new Thread(task, "files-copying");
    thread.setDaemon(true);
    thread.start();
    copy.setDisable(true);
  }

  private void showAlert()
  {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Copy info");
    alert.setHeaderText("Copy success");
    alert.setContentText("All files copied");
    alert.showAndWait();
  }

  private static String getLastModificationDate(File file)
  {
    try
    {
      BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      return DATE_FORMAT.format(attributes.lastModifiedTime().toMillis());
    } catch (IOException e)
    {
      return "undefined";
    }
  }
}
