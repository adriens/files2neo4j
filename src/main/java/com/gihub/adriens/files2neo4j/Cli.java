/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gihub.adriens.files2neo4j;

import java.io.IOException;
import picocli.CommandLine;

/**
 *
 * @author salad74
 */
public class Cli {
    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Displays this help message and quits.")
    private boolean helpRequested = false;
    
    @CommandLine.Option(names={"-O", "--outputdir"}, usageHelp = true, description="Neo4J output dir (MUST be empty)", required=true)
    private String outputDir;
    
    
    @CommandLine.Option(names={"-I", "--inputDir"}, usageHelp = true, description="The input directory to parse", required=true)
    private String inputDir;
    
    public static void main(String[] args){
        Cli app = CommandLine.populateCommand(new Cli(), args);
        
        ///////////////////////////////////////////////////////////////////////
        // fetch parameters
        
        // if help requested, print help (and exit)
        if(app.helpRequested || app.inputDir == null || app.outputDir == null){
            CommandLine.usage(app, System.out,CommandLine.Help.Ansi.AUTO);
            System.exit(0);
        }
        System.out.println("Detected outputDir : <" + app.outputDir + ">");
        System.out.println("Detected inputDir : <" + app.inputDir + ">");
        System.out.println("About to process dirs...");
        try{
            System.out.println("Processing. Be patient it can take a while ...");
            Run run = new Run(app.inputDir, app.outputDir);
            run.report();
            System.out.println("Successfully processed directories and files of <" + app.inputDir+ "> and generated Neo4J datbase in <" + app.outputDir + ">");
        }
        catch(IOException ex){
            System.err.println("Unable to process files and directories : " + ex.getMessage());
            System.exit(1);
        }
        
        
    }
}
