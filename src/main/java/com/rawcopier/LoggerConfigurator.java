package com.rawcopier;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LoggerConfigurator
{
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  public static void configure(Logger logger)
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
        logger.addHandler(handler);
      }
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }

}
