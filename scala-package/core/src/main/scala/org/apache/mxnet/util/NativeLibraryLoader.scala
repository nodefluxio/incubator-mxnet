/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mxnet.util

import java.io._

import org.slf4j.{Logger, LoggerFactory}

private[mxnet] class NativeLibraryLoader

private[mxnet] object NativeLibraryLoader {
  private val logger: Logger = LoggerFactory.getLogger(classOf[NativeLibraryLoader])
  private val libPathInJar = "/lib/native/"
  private val _tempDir: File =
    try {
      val tempDir = File.createTempFile("mxnet", "")
      if (!tempDir.delete || !tempDir.mkdir) {
        throw new IOException(s"Couldn't create directory ${tempDir.getAbsolutePath}")
      }

      /*
       * Different cleanup strategies for Windows and Linux.
       * TODO: shutdown hook won't work on Windows
       */
      if (getUnifiedOSName != "Windows") {
        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = {
            for (f <- tempDir.listFiles()) {
              logger.info("Deleting " + f.getAbsolutePath)
              if (!f.delete()) {
                logger.warn(s"Couldn't delete temporary file ${f.getAbsolutePath}")
              }
            }
            logger.info(s"Deleting ${tempDir.getAbsolutePath}")
            if (!tempDir.delete()) {
              logger.warn(s"Couldn't delete temporary directory ${tempDir.getAbsolutePath}")
            }
          }
        })
        tempDir
      } else {
        throw new RuntimeException("Windows not supported yet.")
      }
    } catch {
      case ex: IOException =>
        logger.error("Couldn't create temporary directory: {}", ex.getMessage)
        null
    }

  /**
   * Find the library as a resource in jar, copy it to a tempfile
   * and load it using System.load(). The name of the library has to be the
   * base name, it is mapped to the corresponding system name using
   * System.mapLibraryName(). e.g., the library "foo" is called "libfoo.so"
   * under Linux and "foo.dll" under Windows, but you just have to pass "foo" to
   * the loadLibrary().
   *
   * @param libname basename of the library
   * @throws UnsatisfiedLinkError if library cannot be founds
   */
  @throws(classOf[UnsatisfiedLinkError])
  def loadLibrary(libname: String) {
    val mappedLibname = System.mapLibraryName(libname)
    val loadLibname: String =
      if (mappedLibname.endsWith("dylib")) {
        logger.info("Replaced .dylib with .jnilib")
        mappedLibname.replace(".dylib", ".jnilib")
      } else {
        mappedLibname
      }
    logger.debug(s"Attempting to load $loadLibname")
    val libFileInJar = libPathInJar + loadLibname
    saveLibraryToTemp("libmxnet.so", "/lib/native/libmxnet.so", true)
    saveLibraryToTemp("libtvm_runtime.so", "/lib/native/libtvm_runtime.so", false)
    saveLibraryToTemp("libgfortran.so.3", "/lib/native/libgfortran.so.3", false)
    saveLibraryToTemp("libquadmath.so.0", "/lib/native/libquadmath.so.0", false)
    saveLibraryToTemp("libdnnl.so.1", "/lib/native/libdnnl.so.1", false)
    saveLibraryToTemp("libdnnl.1.dylib", "/lib/native/libdnnl.1.dylib", false)
    val tempfile: File = saveLibraryToTemp(libname, libFileInJar, true)

    loadLibraryFromFile(libname, tempfile)
  }

  /**
   * Translate all those Windows to "Windows". ("Windows XP", "Windows Vista", "Windows 7", etc.)
   */
  private def unifyOSName(osname: String): String = {
    if (osname.startsWith("Windows")) {
      "Windows"
    }
    osname
  }

  private def getUnifiedOSName: String = {
    unifyOSName(System.getProperty("os.name"))
  }

  @throws(classOf[IOException])
  private def createTempFile(name: String): File = {
    new File(_tempDir, name)
  }

  /**
   * Load a system library from a stream. Copies the library to a temp file
   * and loads from there.
   *
   * @param libname name of the library (just used in constructing the library name)
   * @param tempfile File pointing to the library
   */
  private def loadLibraryFromFile(libname: String, tempfile: File) {
    try {
      logger.debug("Loading library from {}", tempfile.getPath)
      System.load(tempfile.getPath)
    } catch {
      case ule: UnsatisfiedLinkError =>
        logger.error("Couldn't load copied link file: {}", ule.toString)
        throw ule
    }
  }

  /**
    * Load a system library from a stream. Copies the library to a temp file
    * and loads from there.
    *
    * @param libname name of the library (just used in constructing the library name)
    * @param resource String resource path in the jar file
    * @param required true if library is required
    */
  private def saveLibraryToTemp(libname: String, resource: String, required: Boolean): File = {
    try {
      val is: InputStream = getClass.getResourceAsStream(resource)
      if (is == null) {
        if (required) {
          throw new UnsatisfiedLinkError(s"Couldn't find the resource $resource")
        } else {
          null
        }
      } else {
        val tempfile: File = new File(_tempDir, libname)
        val os: OutputStream = new FileOutputStream(tempfile)
        logger.debug("tempfile.getPath() = {}", tempfile.getPath)
        val savedTime: Long = System.currentTimeMillis
        val buf: Array[Byte] = new Array[Byte](8192)
        var len: Int = is.read(buf)
        while (len > 0) {
          os.write(buf, 0, len)
          len = is.read(buf)
        }
        os.close()
        is.close()
        val seconds: Double = (System.currentTimeMillis - savedTime).toDouble / 1e3
        logger.debug(s"Copying $libname took $seconds seconds.")
        tempfile
      }
    } catch {
      case io: IOException =>
        throw new UnsatisfiedLinkError(s"Could not create temp file for $libname")
    }
  }
}
