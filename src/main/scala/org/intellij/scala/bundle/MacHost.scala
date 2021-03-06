package org.intellij.scala.bundle

import java.io._
import java.nio.file.{Files, Paths}
import java.util.Properties
import java.util.concurrent.TimeUnit

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.xfer.{FileSystemFile, InMemorySourceFile, LocalSourceFile}
import org.apache.commons.compress.utils.IOUtils

object MacHost {
  private val Base = "intellij-scala-bundle-builds"

  def createSignedDiskImage(appName: String, ideaBuildNumber: String, hostProperties: File): Unit = {
    val properties = propertiesIn(hostProperties)
    def property(key: String) = properties.getProperty(key).ensuring(_ != null, key)

    createSignedDiskImage(appName, ideaBuildNumber)(
      property("host"), property("fingerprint"), property("login"), property("password"), property("identity"))
  }

  private def propertiesIn(file: File): Properties = {
    val properties = new Properties()
    val input = new BufferedInputStream(new FileInputStream(file))
    properties.load(input)
    input.close()
    properties
  }

  private def createSignedDiskImage(appName: String, ideaBuildNumber: String)(host: String, fingerprint: String, login: String, password: String, identity: String): Unit = {
    System.getProperties.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error")

    implicit val client: SSHClient = new SSHClient()
    client.addHostKeyVerifier(fingerprint)

    println("Connecting...")
    client.connect(host)
    client.authPassword(login, password)

    exec(s"rm -rf $Base")
    exec(s"mkdir $Base")

    upload(binary(s"target/$appName-osx.tar.gz"), s"$Base/$appName-osx.tar.gz")
    exec(s"tar -xzf $Base/$appName-osx.tar.gz -C $Base")
    upload(text("src/main/resources/mac/signbin.sh"), s"$Base/signbin.sh", 755)
    exec(s"$Base/signbin.sh $appName.app $login $password '$identity'")

    exec(s"mkdir $Base/IC-$ideaBuildNumber.exploded")
    exec(s"mv $Base/$appName.app $Base/IC-$ideaBuildNumber.exploded")
    upload(binary("src/main/resources/mac/dmg_background.tiff"), s"$Base/IC-$ideaBuildNumber.png")
    upload(text("src/main/resources/mac/makedmg.pl"), s"$Base/makedmg.pl", 755)
    upload(text("src/main/resources/mac/makedmg.sh"), s"$Base/makedmg.sh", 755)
    exec(s"$Base/makedmg.sh $appName-osx IC-$ideaBuildNumber")

    download(s"$Base/$appName-osx.dmg", s"target/$appName-osx.dmg")

    exec(s"rm -rf $Base")

    client.disconnect()

    println("Done.")
  }

  private def upload(source: LocalSourceFile, destination: String, perms: Int = -1)(implicit client: SSHClient): Unit = {
    val sftp = client.newSFTPClient()
    println(s"scp $source $destination")
    sftp.put(source, destination)
    if (perms != -1) {
      sftp.chmod(destination, Integer.parseInt(perms.toString, 8))
    }
    sftp.close()
  }

  private def download(source: String, destination: String)(implicit client: SSHClient): Unit = {
    val sftp = client.newSFTPClient()
    println(s"scp $source $destination")
    sftp.get(source, destination)
    sftp.close()
  }

  private def binary(file: String): LocalSourceFile = new FileSystemFile(file)

  private def text(file: String): LocalSourceFile = {
    val bytes = new String(Files.readAllBytes(Paths.get(file))).replaceAll("\r", "").getBytes

    new InMemorySourceFile {
      override def getName: String = file

      override def getLength: Long = bytes.length

      override def getInputStream: InputStream = new ByteArrayInputStream(bytes)
    }
  }

  private def exec(command: String)(implicit client: SSHClient): Unit = {
    println("ssh " + command)

    val session = client.startSession()

    val cmd = session.exec(command)

    IOUtils.copy(cmd.getInputStream, System.out)
    IOUtils.copy(cmd.getInputStream, System.err)
    cmd.join(5, TimeUnit.SECONDS)

    session.close()

    if (cmd.getExitErrorMessage != null) {
      System.err.println("Error message " + cmd.getExitErrorMessage)
    }

    if (cmd.getExitStatus != 0) {
      System.err.println("Exit status: " + cmd.getExitStatus)
      System.exit(-1)
    }
  }
}
