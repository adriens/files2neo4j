/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gihub.adriens.files2neo4j;

import org.neo4j.graphdb.Label;

/**
 *
 * @author salad74
 */
public enum FileNodeTypes implements Label{
    FILE, DIRECTORY, SYMLINK, UNKNOWN
}
