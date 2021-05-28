package com.rawcopier;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FileUtils;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FilenameUtils;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.filefilter.IOFileFilter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RawCopier implements Initializable
{
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private static final String FILE_EXTENSION = "cr2";
  private static final Logger LOGGER = Logger.getLogger(RawCopier.class.getName());

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

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle)
  {
    initializeLogger();

    fillSource();
    fillTarget();
  }

  private void initializeLogger()
  {
    try
    {
      File logs = new File(System.getProperty("user.home") + "/.RawCopier/logs/");
      if (logs.mkdirs() || logs.exists())
      {
        FileHandler handler = new FileHandler(logs.getAbsolutePath() + "/" + DATE_FORMAT.format(new Date()) + ".log");
        handler.setFormatter(new Formatter()
        {
          @Override
          public String format(LogRecord record)
          {
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(record.getMillis()));
            String format = "%s - %s#%s : %s\n";
            return String.format(format, date, record.getSourceClassName(), record.getSourceMethodName(), record.getMessage());
          }
        });
        LOGGER.addHandler(handler);
      }
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }

  private void fillSource()
  {
    sourceDir = fill("dir.source", sourceDir, sourceFolderLabel, "--Source folder--");
    copy.setDisable(sourceDir == null || targetDir == null);
  }

  private void fillTarget()
  {
    targetDir = fill("dir.target", targetDir, targetFolderLabel, "--Target folder--");
    copy.setDisable(sourceDir == null || targetDir == null);
  }

  private File fill(String prefKey, File dir, Label label, String defaultValue)
  {
    if (dir == null)
    {
      String source = Main.PREF.get(prefKey, defaultValue);
      dir = new File(source);
    }
    if (dir.exists())
    {
      String path = dir.getAbsolutePath();
      Main.PREF.put(prefKey, path);
      label.setText(path);
      Optional.ofNullable(dir.list())
        .map(files -> files.length)
        .ifPresent(size -> label.setTooltip(new Tooltip("Files count: " + size)));

      return dir;
    }
    return null;
  }

  public void chooseSource()
  {
    sourceDir = choose();
    fillSource();
  }

  public void chooseTarget()
  {
    targetDir = choose();
    fillTarget();
  }

  private File choose()
  {
    DirectoryChooser directoryChooser = new DirectoryChooser();
    return directoryChooser.showDialog(pane.getScene().getWindow());
  }

  private Collection<File> readFiles(File dir)
  {
    return FileUtils.listFiles(dir, new IOFileFilter()
    {
      @Override
      public boolean accept(File file)
      {
        return FILE_EXTENSION.equalsIgnoreCase(FilenameUtils.getExtension(file.getName()));
      }

      @Override
      public boolean accept(File file, String s)
      {
        return false;
      }
    }, DirectoryFileFilter.DIRECTORY);
  }

  public void copy()
  {
    filesCount = 0;
    copied = 0;

    Collection<File> files = readFiles(sourceDir);
    Set<String> targetFiles = readFiles(targetDir).stream()
      .map(File::getName)
      .collect(Collectors.toSet());
    files.removeIf(p -> targetFiles.contains(p.getName()));
    filesCount = files.size();
    if (filesCount == 0)
    {
      Alert alert = new Alert(Alert.AlertType.WARNING);
      alert.setTitle("Copy fail");
      alert.setHeaderText("Nothing to copy!");
      alert.setContentText("Target directory already contains all source files. Please select other directories.");
      alert.showAndWait();
      return;
    }

    Task<Void> task = new Task<>()
    {
      @Override
      protected Void call()
      {
        updateProgress(0, filesCount);

        files.stream()
          .collect(Collectors.groupingBy(RawCopier::getLastModificationDate))
          .forEach(this::copy);
        return null;
      }

      private void copy(String dirName, Collection<File> files)
      {
        File target = createTargetDirectory(dirName);
        if (target == null || !target.exists())
        {
          return;
        }

        files.forEach(file -> copySingleFile(target, file));
      }

      private void copySingleFile(File target, File file)
      {
        try
        {
          FileUtils.copyFileToDirectory(file, target);
          updateProgress(++copied, filesCount);
        } catch (IOException e)
        {
          LOGGER.log(Level.SEVERE, "Cannot copy file to directory");
        }
      }

      private File createTargetDirectory(String dirName)
      {
        File directory = new File(targetDir, dirName);
        try
        {
          return directory.exists() ? directory : Files.createDirectory(directory.toPath()).toFile();
        } catch (IOException e)
        {
          LOGGER.log(Level.SEVERE, "Cannot create target directory");
          return null;
        }
      }
    };

    task.setOnSucceeded(e -> showAlert());

    progressBar.progressProperty().bind(task.progressProperty());

    Thread thread = new Thread(task, "files-copying");
    thread.setDaemon(true);
    thread.start();
  }

  private void showAlert()
  {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Copy info");
    alert.setHeaderText("Copy success");
    alert.setContentText("All files copied to selected directory");
    alert.showAndWait();
    copy.setDisable(true);
  }

  private static String getLastModificationDate(File file)
  {
    try
    {
      BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      return DATE_FORMAT.format(attributes.lastModifiedTime().toMillis());
    } catch (IOException e)
    {
      LOGGER.log(Level.WARNING, "Cannot extract last modification date");
      return "undefined";
    }
  }
}
