package com.firefly.intellij.lsp;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Go to Declaration handler for Firefly using LSP.
 * Provides "Go to Definition" functionality by querying the LSP server.
 */
public class FireflyGotoDeclarationHandler implements GotoDeclarationHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(FireflyGotoDeclarationHandler.class);
    
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        
        PsiFile psiFile = sourceElement.getContainingFile();
        if (psiFile == null) {
            return null;
        }
        
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        
        Project project = sourceElement.getProject();
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        
        if (lspClient == null || !lspClient.isInitialized()) {
            LOG.warn("LSP client not initialized");
            return null;
        }
        
        try {
            // Convert offset to LSP position
            int line = editor.getDocument().getLineNumber(offset);
            int lineStartOffset = editor.getDocument().getLineStartOffset(line);
            int character = offset - lineStartOffset;
            
            // Create LSP definition request
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
            Position position = new Position(line, character);
            DefinitionParams params = new DefinitionParams(textDocument, position);
            
            // Request definition from LSP server
            CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> future = 
                lspClient.getLanguageServer().getTextDocumentService().definition(params);
            
            Either<List<? extends Location>, List<? extends LocationLink>> result = future.get(3, TimeUnit.SECONDS);
            
            if (result == null) {
                return null;
            }
            
            List<? extends Location> locations = result.isLeft() ? result.getLeft() : null;
            if (locations == null || locations.isEmpty()) {
                return null;
            }
            
            // Convert LSP locations to PsiElements
            PsiElement[] targets = new PsiElement[locations.size()];
            for (int i = 0; i < locations.size(); i++) {
                Location location = locations.get(i);
                PsiElement target = locationToPsiElement(project, location);
                if (target != null) {
                    targets[i] = target;
                }
            }
            
            return targets;
            
        } catch (Exception e) {
            LOG.error("Error getting definition", e);
            return null;
        }
    }
    
    /**
     * Convert LSP Location to PsiElement.
     */
    private PsiElement locationToPsiElement(Project project, Location location) {
        try {
            URI uri = new URI(location.getUri());
            VirtualFile file = com.intellij.openapi.vfs.VfsUtil.findFileByURL(uri.toURL());
            
            if (file == null) {
                return null;
            }
            
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return null;
            }
            
            // Convert LSP position to offset
            Range range = location.getRange();
            int line = range.getStart().getLine();
            int character = range.getStart().getCharacter();
            
            com.intellij.openapi.editor.Document document = 
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file);
            
            if (document == null) {
                return null;
            }
            
            int lineStartOffset = document.getLineStartOffset(line);
            int offset = lineStartOffset + character;
            
            return psiFile.findElementAt(offset);
            
        } catch (Exception e) {
            LOG.error("Error converting location to PsiElement", e);
            return null;
        }
    }
}

