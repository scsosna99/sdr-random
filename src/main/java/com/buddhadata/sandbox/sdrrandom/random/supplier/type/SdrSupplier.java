/*
 * Copyright (c) 2019  Scott C. Sosna  ALL RIGHTS RESERVED
 *
 */

package com.buddhadata.sandbox.sdrrandom.random.supplier.type;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.Lifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Random number generator that uses the data received from the software-defined radio - specifically, rtl_fm - to generate
 * new bits used when generating the random number.  Everything else in the default Random class for generating the random numbers
 * remains the same.
 */
@Component
public class SdrSupplier
  implements LongSupplier, Lifecycle {

  /**
   * boolean for determining whether the thread reading the SDR data should keep working.
   */
  private final AtomicBoolean reading = new AtomicBoolean (false);

  /**
   * The circular buffer into which the data is stored.
   */
  private final byte[] circular;

  /**
   * the size of the buffer, used for reference when reading.
   */
  private final int bufferSize;

  /**
   * current reading position in the buffer to use for generating a random number
   */
  private Integer readPos = 0;

  /**
   * Process that's running in the background
   */
  private Process sdrProcess;

  /**
   * The frequency in which to tune the RTL software-defined radio
   */
  @Value("${jukebox.random.sdr.frequency:92.5}")
  private String frequency;

  /**
   * The pipe from which the random data is read from, i.e., the input from the software-defined radio stream.
   */
  @Value("${jukebox.random.sdr.pipename:unknown}")
  private String pipeName;

  /**
   * Command that will capture FM radio station to provide random number data
   */
  private final String RLT_COMMAND =  "rtl_fm -g 50 -f %s -M wfm -s 180k -E deemp | tee %s | play -q -r 180k -t raw -e s -b 16 -c 1 -V1 - lowpass 16k";

  /**
   * if not otherwise specified, the size of the buffer into which data is read
   */
  private static int DEFAULT_BUFFER_SIZE = 20480;

  /**
   * Constructor
   * @param frequency frequency in which to tune the RTL software-defined radio
   * @param pipeName pipe from which the random data is read from, i.e., the input from the software-defined radio stream.
   * @param bufferSize how big of a circular buffer to create/use
   */
  public SdrSupplier(String frequency,
                     String pipeName,
                     int bufferSize) {

    super();

    this.frequency = frequency;
    this.pipeName = pipeName;
    this.bufferSize = bufferSize;
    this.circular = new byte [bufferSize];
  }

  /**
   * Constructor
   * @param frequency frequency in which to tune the RTL software-defined radio
   * @param pipeName pipe from which the random data is read from, i.e., the input from the software-defined radio stream.
   */
  public SdrSupplier(String frequency,
                     String pipeName) {
    this (frequency, pipeName, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Default constructor;
   */
  public SdrSupplier() {
    this.bufferSize = DEFAULT_BUFFER_SIZE;
    this.circular = new byte [bufferSize];
  }

  /**
   * Returns a long value based on the bytes in the circular buffer.
   * @return
   */
  public long getAsLong() {

    //  Get the read position and increment immediately, gives us some level of multithreaded-ness.  Even though
    //  the bytes are going to constantly be rewritten as new data is received from the SDR stream, we'll just march
    //  through the buffer reading gradually based on how many random numbers are generated.
    int currentRead;
    synchronized (readPos) {
      currentRead = readPos;
      readPos += 8;
      if (readPos >= bufferSize) {
        readPos = 0;
      }
    }

    // Construct a long by shifting the existing answer 8 bits to the left and or'ing the current byte.
    ByteBuffer buff = ByteBuffer.allocate(Long.BYTES);
    buff.put(circular, currentRead, 8);
    buff.flip();
    long toReturn =  buff.getLong();


    //  Return the newly-built long.
    return toReturn;
  }

  /**
   * Spring Lifecycle: Start the thread for reading data from the pipe.
   */
  public void start () {

    try {
      System.out.println("Checking for pipe file.");
      File pipe = new File(pipeName);
      if (!pipe.exists()) {
        //  Pipe does not exist so create.
        System.out.println ("Creating pipe for sdr data");
        Runtime.getRuntime().exec("mkfifo " + pipeName);
      }

      //  Start the rtl_fm process that will provide us data for random numbers.
      String[] cmd = {
        "/bin/sh",
        "-c",
        String.format (RLT_COMMAND, frequency, pipeName)
      };
      System.out.println ("Starting rtl_fm and play");
      sdrProcess = Runtime.getRuntime().exec (cmd, createEnvironment());

      //  Give the process some time to start up
      sdrProcess.waitFor(5, TimeUnit.SECONDS);
    } catch (IOException | InterruptedException ioe ) {
      //  Exception while setting up pipe or rtl_fm
      System.out.println ("Exception occurred: " + ioe);
      ioe.printStackTrace();
    }

    System.out.println ("Starting SdrSupplier: " );
    reading.set(true);
    new Thread(() -> readData()).start();
  }

  /**
   * Spring Lifecycle: Stop the thread for reading data from the pipe.
   */
  public void stop() {

    reading.set(false);
    sdrProcess.destroy();
  }

  /**
   * Spring Lifecycle: is the supplier running.
   * @return
   */
  public boolean isRunning () {
    return reading.get();
  }

  /**
   * Read the SDR data from the pipe named and stuff it into the buffer.
   */
  private void readData () {

    try (FileInputStream fis = new FileInputStream (pipeName)) {

      //  Need an input data in which to read the data.
      byte[] input = new byte[2048];
      int writePos = 0;

      //  Keep reading data until the flag is turned off/disabled/whatever.
      while (reading.get()) {

        //  Read data
        int read = fis.read(input);
        int remaining = circular.length - writePos;

        //  If we don't exceed the end of the array based on where we are and how much we read, single array copy suffices.
        if (read < remaining) {
          System.arraycopy(input, 0, circular, writePos, read);
          writePos += read;
        } else {
          //  we're going past the end of the circular buffer so need to fill in the end and then wrap around to the beginning.
          System.arraycopy(input, 0, circular, writePos, remaining);
          System.arraycopy(input, remaining, circular, 0, writePos = read - remaining);
        }
      }

    } catch (Exception e) {
      System.out.println ("**** Exception reading SDR data: " + e);
      e.printStackTrace();
    }

    System.out.println ("**** Stopping thread for reading data.");
    sdrProcess.destroy();
  }

  /**
   * Get the existing environment to pass to process.
   * @return
   */
  private String[] createEnvironment() {

    List<String> env = new ArrayList<>(System.getenv().size());
    for (Map.Entry one : System.getenv().entrySet()) {
      env.add (one.getKey() + "=" + one.getValue());
    }

    return env.toArray(new String[env.size()]);
  }
}
