package com.rawcopier;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressBar;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FileUtils;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.FilenameUtils;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.maven.surefire.shade.common.org.apache.commons.io.filefilter.IOFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Leonzio20
 */
public final class FileHandler
{
  private static final Logger LOGGER = LoggerConfigurator.configure(RawCopier.class.getName());
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private static final String FILE_EXTENSION = "cr2";

  private final Set<DirectoryChangeListener> sourceDirectoryChangeListeners = new HashSet<>();
  private final Set<DirectoryChangeListener> targetDirectoryChangeListeners = new HashSet<>();
  private final Collection<File> sourceFiles = new HashSet<>();
  private final Collection<File> targetFiles = new HashSet<>();

  private File sourceDir;
  private File targetDir;

  private long copied = 0;

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

  public void init(String sourcePath, String targetPath)
  {
    Optional.ofNullable(sourcePath)
      .map(File::new)
      .ifPresent(this::fillSource);

    Optional.ofNullable(targetPath)
      .map(File::new)
      .ifPresent(this::fillTarget);
  }

  public void fillSource(File dir)
  {
    if (dir == null)
    {
      return;
    }

    this.sourceDir = dir;
    sourceFiles.clear();
    sourceFiles.addAll(readFiles(dir));
    String path = dir.getAbsolutePath();
    sourceDirectoryChangeListeners.forEach(listener -> listener.execute(path, sourceFiles.size()));
  }

  public void fillTarget(File dir)
  {
    if (dir == null)
    {
      return;
    }
    this.targetDir = dir;
    targetFiles.clear();
    targetFiles.addAll(readFiles(dir));
    String path = dir.getAbsolutePath();
    targetDirectoryChangeListeners.forEach(listener -> listener.execute(path, targetFiles.size()));
  }

  private Collection<File> readFiles(File dir)
  {
    return FileUtils.listFiles(dir, createFileFilter(), DirectoryFileFilter.DIRECTORY);
  }

  private IOFileFilter createFileFilter()
  {
    return new IOFileFilter()
    {
      @Override
      public boolean accept(File file)
      {
        String fileName = file.getName();
        String extension = FilenameUtils.getExtension(fileName);
        return FILE_EXTENSION.equalsIgnoreCase(extension);
      }

      @Override
      public boolean accept(File file, String s)
      {
        return false;
      }
    };
  }

  public void performCopy(ProgressBar progressBar)
  {
    copied = 0;

    Set<String> targetFilesNames = this.targetFiles.stream()
      .map(File::getName)
      .collect(Collectors.toSet());
    sourceFiles.removeIf(p -> targetFilesNames.contains(p.getName()));
    if (!isValid())
    {
      showNothingToCopyAlert();
      return;
    }

    Task<Void> task = new CopyTask();

    task.setOnSucceeded(e -> showSuccessfulCopyInfo());
    progressBar.progressProperty().bind(task.progressProperty());
    Thread thread = new Thread(task, "files-copying");
    thread.setDaemon(true);
    thread.start();
  }

  private boolean isValid()
  {
    return !sourceFiles.isEmpty() && targetDir != null;
  }

  private void showNothingToCopyAlert()
  {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Copy fail");
    alert.setHeaderText("Nothing to copy!");
    alert.setContentText("Source directory does not contain any available files to copy. Please select other directory.");
    alert.showAndWait();
  }

  private void showSuccessfulCopyInfo()
  {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Copy info");
    alert.setHeaderText("Copy success");
    alert.setContentText("All files copied to selected directory.");
    alert.showAndWait();
  }

  public void addSourceDirectoryChangeListener(DirectoryChangeListener listener)
  {
    sourceDirectoryChangeListeners.add(listener);
  }

  public void addTargetDirectoryChangeListener(DirectoryChangeListener listener)
  {
    targetDirectoryChangeListeners.add(listener);
  }

  /**
   * @author Leonzio20
   */
  public interface DirectoryChangeListener
  {
    void execute(String directoryPath, int filesCount);
  }

  /**
   * @author Leonzio20
   */
  private class CopyTask extends Task<Void>
  {
    @Override
    protected Void call()
    {
      updateProgress(0, sourceFiles.size());

      sourceFiles.stream()
        .collect(Collectors.groupingBy(FileHandler::getLastModificationDate))
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
      LOGGER.log(Level.INFO, "Total files copied: " + (copied - 1));
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

    private void copySingleFile(File target, File file)
    {
      try
      {
        FileUtils.copyFileToDirectory(file, target);
        updateProgress(++copied, sourceFiles.size());
        LOGGER.log(Level.INFO, "File " + file.getName() + " copied");
      } catch (IOException e)
      {
        LOGGER.log(Level.SEVERE, "Cannot copy file to directory");
      }
    }
  }
}
