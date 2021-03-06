/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gihub.adriens.files2neo4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Iterator;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;


import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
/**
 *
 * @author salad74
 */
public class Run {

    private String outputGraphDir;
    private String directory;

    final static Logger logger = LoggerFactory.getLogger(Run.class);
    
    private GraphDatabaseFactory dbFactory;
    private GraphDatabaseService dbService;

    public Iterator<File> getFiles(String dir, String[] extensions, boolean recursive) {
        Iterator<File> fileIter = FileUtils.iterateFiles(new File(dir), extensions, recursive);
        return fileIter;
    }

    public Iterator<File> getFiles(String dir, boolean recursive) {
        return getFiles(dir, null, recursive);
    }

    public Iterator<File> getFiles(String dir) {

        return getFiles(dir, true);
    }

    public Iterator<File> getFiles() {
        return getFiles(getDirectory());
    }

    public Iterator<File> getDirs() {
        return FileUtils.listFilesAndDirs(new File(getDirectory()), new NotFileFilter(TrueFileFilter.INSTANCE), DirectoryFileFilter.DIRECTORY).iterator();
    }

    public void setUpNEO4J() {
        // setUp NEO4J
        setDbFactory(new GraphDatabaseFactory());
        setDbService(getDbFactory().newEmbeddedDatabase(new File(getOutputGraphDir())));

    }

    ////
    public void generateNeo4jGraphOutputDirectory(String outDir) throws IOException {
        File outputDir = new File(outDir);

        if (outputDir.exists()) {
            if (outputDir.isDirectory()) {
                // is it empty ?
                if (outputDir.list().length == 0) {
                    logger.warn("Dir exists and is empty : <" + outputDir.list().length + ">");
                    setUpNEO4J();
                } else {
                    logger.error("Dir is not empty. Clean-it up : <" + outputDir.list().length + ">");
                    System.exit(1);
                }

            }
        } else {
            logger.warn("Does not exist");
            // create it !
            outputDir.mkdir();
            logger.info("Directory created");
            setUpNEO4J();
        }
    }

    public void report() throws IOException {
        generateNeo4jGraphOutputDirectory();

        feedFiles();
        feedDirectories();
        createIndexes();
        linkFilesToDirs();
        linkDirsToDirs();
        
        //linkSymlinksFiles();
    }

    public void generateNeo4jGraphOutputDirectory() throws IOException {
        generateNeo4jGraphOutputDirectory(getOutputGraphDir());
    }

    public void feedFiles() {
        logger.info("Feeding files ...");
        ContentHandler contenthandler = new BodyContentHandler();
      Metadata metadata = new Metadata();
        FileInputStream is = null;
        // here we go , we have a proper output directory
        // let's fill it with files nodes
        try (Transaction tx = getDbService().beginTx()) {
            Iterator<File> filesIter = getFiles(getDirectory());
            
            File lFile;
            while (filesIter.hasNext()) {
                lFile = filesIter.next();
                if (lFile.isFile() ) {

                    
                    Node fileNode = dbService.createNode(FileNodeTypes.FILE);
                    logger.debug("Adding file : " + lFile.getName() + " on node " + fileNode.getId());
                    fileNode.setProperty("canExecute", lFile.canExecute());
                    fileNode.setProperty("canRead", lFile.canRead());
                    fileNode.setProperty("canWrite", lFile.canWrite());
                    fileNode.setProperty("absolutePath", lFile.getAbsolutePath());
                    fileNode.setProperty("name", lFile.getName());
                    fileNode.setProperty("parent", lFile.getParent());
                    fileNode.setProperty("path", lFile.getPath());
                    fileNode.setProperty("lastModified", lFile.lastModified());
                    fileNode.setProperty("length", lFile.length());
                    fileNode.setProperty("isHidden", lFile.isHidden());

                    if (lFile.getParentFile().getAbsolutePath() != null) {
                        fileNode.setProperty("parentAbsoluteFile", lFile.getParentFile().getAbsolutePath());
                    }

                    if (Files.isSymbolicLink(FileSystems.getDefault().getPath(lFile.getAbsolutePath()))) {
                        fileNode.setProperty("isSymbolicLink", true);
                    } else {
                        fileNode.setProperty("isSymbolicLink", false);
                    }
                    // set posix file++ permissions
                    try{
                        Path path = Paths.get(lFile.getAbsolutePath());
                        Set<PosixFilePermission> set = Files.getPosixFilePermissions(path);
                        fileNode.setProperty("posixFilePermissions", PosixFilePermissions.toString(set));
                    }
                    catch(IOException ex){
                        logger.debug("Unable to get posixFilePersmiisions : " + ex.getMessage());
                    }
                    catch(UnsupportedOperationException ex){
                        logger.debug("posixFilePersmiisions operations not implemented on this platform, will be skipped.");
                    }
                    // set mime-type
                    /*
                    try{
                        fileNode.setProperty("mimeType", Files.probeContentType(Paths.get(lFile.getAbsolutePath())));
                    }
                    catch(IOException ex){
                        System.err.println("Unable to compute mime-type : " + ex.getMessage());
                    }
                    */
                    // set mimetype
                    try{
                        is = new FileInputStream(lFile);
                        metadata.set(Metadata.RESOURCE_NAME_KEY, lFile.getName());
                        Parser parser = new AutoDetectParser();
                        parser.parse(is, contenthandler, metadata,new ParseContext());
                        fileNode.setProperty("mime-type", metadata.get(Metadata.CONTENT_TYPE));
                        is.close();
                    }
                    catch(Exception ex){
                        logger.warn("Not able to compute mime-type : " + ex.getMessage() + " . Computation skipped.");
                    }
                }
            }
            tx.success();
        }

    }

    public void createIndexes(){
        try (Transaction tx = getDbService().beginTx()) {
            // https://dzone.com/articles/indexing-neo4j-overview
            // CREATE INDEX ON :Person(name);
            logger.info("Creating indexes ...");
            getDbService().execute("create index on :FILE(absolutePath)");
            tx.success();
            getDbService().execute("create index on :DIRECTORY(absolutePath)");
            tx.success();
            tx.close();
            logger.info("Indexes created.");
        }
    }
    public void feedDirectories() {
        // here we go , we have a proper output directory
        // let's fill it with files nodes
        logger.info("Feeding directories ...");
        try (Transaction tx = getDbService().beginTx()) {
            Iterator<File> filesIter = getDirs();
            File lFile;

            Node fileNode;
// now let's add subdirs
            while (filesIter.hasNext()) {
                lFile = filesIter.next();
                logger.info("Adding dir : " + lFile.getAbsolutePath());
                fileNode = dbService.createNode(FileNodeTypes.DIRECTORY);
                logger.debug("Node id : " + fileNode.getId());
                fileNode.setProperty("canExecute", lFile.canExecute());
                fileNode.setProperty("canRead", lFile.canRead());
                fileNode.setProperty("canWrite", lFile.canWrite());
                fileNode.setProperty("absolutePath", lFile.getAbsolutePath());
                fileNode.setProperty("name", lFile.getName());

                if (lFile.getParent() != null) {
                    fileNode.setProperty("parent", lFile.getParent());
                    fileNode.setProperty("parentAbsoluteFile", lFile.getParentFile().getAbsolutePath());
                }

                fileNode.setProperty("path", lFile.getPath());
                fileNode.setProperty("lastModified", lFile.lastModified());
                fileNode.setProperty("length", lFile.length());
                fileNode.setProperty("isHidden", lFile.isHidden());

                fileNode.setProperty("freeSpace", lFile.getFreeSpace());
                fileNode.setProperty("totalSpace", lFile.getTotalSpace());
                fileNode.setProperty("usableSpace", lFile.getUsableSpace());

                if (Files.isSymbolicLink(FileSystems.getDefault().getPath(lFile.getAbsolutePath()))) {
                    fileNode.setProperty("isSymbolicLink", true);
                } else {
                    fileNode.setProperty("isSymbolicLink", false);
                }
                // set posix file++ permissions
                    try{
                        Path path = Paths.get(lFile.getAbsolutePath());
                        Set<PosixFilePermission> set = Files.getPosixFilePermissions(path);
                        fileNode.setProperty("posixFilePermissions", PosixFilePermissions.toString(set));
                    }
                    catch(IOException ex){
                        logger.debug("Unable to get posixFilePersmiisions : " + ex.getMessage());
                    }
                    catch(UnsupportedOperationException ex){
                        logger.debug("Unable to get posixFilePersmiisions on this platform : skipped.");
                    }
            }
            tx.success();
        }

    }

    //fetch all files uin graph and attach them to their directory
    public void linkFilesToDirs() {
        //fetch files in the graph
        logger.info("Linking files to dirs...");
        try (Transaction tx = getDbService().beginTx()) {
            //dbService.findNodes(FileNodeTypes.FILE).
            Node fileNode;
            ResourceIterator<Node> iter = dbService.findNodes(FileNodeTypes.FILE);

            while (iter.hasNext()) {
                fileNode = iter.next();
                // we have the file

                // check if it has a parent dir
                if (fileNode.getProperty("parentAbsoluteFile") != null) {
                    // find the parent dir node
                    Node targetDir = dbService.findNode(FileNodeTypes.DIRECTORY, "absolutePath", fileNode.getProperty("parentAbsoluteFile"));
                    if(targetDir != null){
                        //Relationship rel = dbService.findNode(DatabaseNodeType.TABLE_COLUMN, "fullName", fkColRef.getForeignKeyColumn().getFullName()).createRelationshipTo(dbService.findNode(DatabaseNodeType.FOREIGN_KEY, "fullName", fk.getFullName()), SchemaRelationShips.IS_COLUMN_OF_FK);
                    fileNode.createRelationshipTo(targetDir, FileRelationshipType.IS_IN_DIRECTORY);
                    }
                    else{
                        logger.warn("Unable to find target node (parent dir) : dir for " + fileNode.getProperty("absolutePath"));
                    }
                    
                }

            }
            tx.success();
        }
    }

    public void linkDirsToDirs() {
        //fetch files in the graph
        logger.info("Linking dirs to dirs...");
        try (Transaction tx = getDbService().beginTx()) {
            //dbService.findNodes(FileNodeTypes.FILE).
            Node dirNode;
            ResourceIterator<Node> iter = dbService.findNodes(FileNodeTypes.DIRECTORY);

            while (iter.hasNext()) {
                dirNode = iter.next();
                // we have the dir
                logger.debug("Fetching on node " + dirNode.getId() + " : " + dirNode.getProperty("absolutePath"));

                // check if it has a parent dir
                logger.debug("Looking for parent node of " + dirNode.getId());
                if (dirNode.hasProperty("parentAbsoluteFile")) {
                    if (dirNode.getProperty("parentAbsoluteFile") != null) {
                        // find the parent dir node
                        Node targetDir = dbService.findNode(FileNodeTypes.DIRECTORY, "absolutePath", dirNode.getProperty("parentAbsoluteFile"));
                        //dirNode.createRelationshipTo(targetDir, FileRelationshipType.IS_IN_DIRECTORY);
                        if(targetDir != null){
                            //Relationship rel = dbService.findNode(DatabaseNodeType.TABLE_COLUMN, "fullName", fkColRef.getForeignKeyColumn().getFullName()).createRelationshipTo(dbService.findNode(DatabaseNodeType.FOREIGN_KEY, "fullName", fk.getFullName()), SchemaRelationShips.IS_COLUMN_OF_FK);
                            dirNode.createRelationshipTo(targetDir, FileRelationshipType.IS_IN_DIRECTORY);
                        }
                        else{
                            logger.warn("Unable to find target node (parent dir) : dir for " + dirNode.getProperty("absolutePath"));
                        }
                        
                    }
                }

            }
            tx.success();
        }
    }

    public Run(String dir, String outputgraphDir) {
        setDirectory(dir);
        setOutputGraphDir(outputgraphDir);

    }
    
    public void linkSymlinksFiles() throws IOException{
        try (Transaction tx = getDbService().beginTx()) {
            //dbService.findNodes(FileNodeTypes.FILE).
            Path link;
            Node fileNode;
            ResourceIterator<Node> iter = dbService.findNodes(FileNodeTypes.FILE);
            Path targetPath;
            Node targetNode;
            while (iter.hasNext()) {
                fileNode = iter.next();
                // we have the file

                if(fileNode.hasProperty("isSymbolicLink")){
                    if(fileNode.getProperty("isSymbolicLink").toString().equalsIgnoreCase("true")){
                        // the node is a symlink
                    // let's find the target of the symlink
                    
                    logger.debug("Symlink of <" + fileNode.getProperty("absolutePath"));
                    // path of the link itself
                    link = Paths.get(fileNode.getProperty("absolutePath").toString());
                    logger.debug("link : <" + link.toString());
                    
                    targetPath = (Files.readSymbolicLink(link));//.toRealPath(LinkOption.NOFOLLOW_LINKS);
                    
                    logger.debug("target path : <" + targetPath.toString());
                    logger.debug("parent target path : <" + targetPath.getParent());
                    logger.debug("absolute target path : <" + targetPath.toAbsolutePath());
                    //link = Paths.get(fileNode.getProperty("absolutePath").toString());
                    
                    //targetPath = Files.readSymbolicLink(link).toRealPath(LinkOption.NOFOLLOW_LINKS);
                    
                    //System.out.println("Found symbolic link : <" + fileNode.getProperty("absolutePath") + "> -> <" + targetPath.toString() + ">");
                    //System.out.println("Found symbolic link : <" + fileNode.getProperty("absolutePath") + "> -> <" + Files.readSymbolicLink(link, LinkOption.NOFOLLOW_LINKS).getFileName().toString() + ">");
                    /*
                    // find the taget node
                    targetNode = dbService.findNode(FileNodeTypes.FILE, "absolutePath", targetPath);
                    // if target of symink is in the graph, create the link in the grap
                    if(targetNode != null){
                        fileNode.createRelationshipTo(targetNode, FileRelationshipType.LINKS_TO);
                    }*/
                    
                    
                    }
                }

            }
            tx.success();
        }
        
    }

    /**
     * @return the outputGraphDir
     */
    public String getOutputGraphDir() {
        return outputGraphDir;
    }

    /**
     * @param outputGraphDir the outputGraphDir to set
     */
    public void setOutputGraphDir(String outputGraphDir) {
        this.outputGraphDir = outputGraphDir;
    }

    /**
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }

    /**
     * @param directory the directory to set
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public static void main(String args[]) throws IOException {

        String dir = args[0];
        String output = args[1];

        Run run = new Run(dir, output);
        run.report();

    }

    /**
     * @return the dbFactory
     */
    public GraphDatabaseFactory getDbFactory() {
        return dbFactory;
    }

    /**
     * @param dbFactory the dbFactory to set
     */
    public void setDbFactory(GraphDatabaseFactory dbFactory) {
        this.dbFactory = dbFactory;
    }

    /**
     * @return the dbService
     */
    public GraphDatabaseService getDbService() {
        return dbService;
    }

    /**
     * @param dbService the dbService to set
     */
    public void setDbService(GraphDatabaseService dbService) {
        this.dbService = dbService;
    }

}
