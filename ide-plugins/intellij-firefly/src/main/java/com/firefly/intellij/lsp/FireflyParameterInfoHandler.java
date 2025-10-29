package com.firefly.intellij.lsp;

import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Parameter Info handler for Firefly using LSP.
 * Provides signature help/parameter hints by querying the LSP server.
 */
public class FireflyParameterInfoHandler implements ParameterInfoHandler<PsiElement, SignatureInformation> {
    
    private static final Logger LOG = LoggerFactory.getLogger(FireflyParameterInfoHandler.class);
    
    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();
        
        return file.findElementAt(offset);
    }
    
    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null) {
            return;
        }
        
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        
        Project project = element.getProject();
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        
        if (lspClient == null || !lspClient.isInitialized()) {
            return;
        }
        
        try {
            // Convert offset to LSP position
            int offset = context.getOffset();
            com.intellij.openapi.editor.Document document = 
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile);
            
            if (document == null) {
                return;
            }
            
            int line = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(line);
            int character = offset - lineStartOffset;
            
            // Create LSP signature help request
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
            Position position = new Position(line, character);
            SignatureHelpParams params = new SignatureHelpParams(textDocument, position);
            
            // Request signature help from LSP server
            CompletableFuture<SignatureHelp> future = 
                lspClient.getLanguageServer().getTextDocumentService().signatureHelp(params);
            
            SignatureHelp result = future.get(3, TimeUnit.SECONDS);
            
            if (result == null || result.getSignatures() == null || result.getSignatures().isEmpty()) {
                return;
            }
            
            // Set the signature information
            context.setItemsToShow(result.getSignatures().toArray(new SignatureInformation[0]));
            context.showHint(element, offset, this);
            
        } catch (Exception e) {
            LOG.error("Error getting signature help", e);
        }
    }
    
    @Override
    public @Nullable PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();
        
        return file.findElementAt(offset);
    }
    
    @Override
    public void updateParameterInfo(@NotNull PsiElement element, @NotNull UpdateParameterInfoContext context) {
        // Update active parameter based on cursor position
        // For now, keep it simple
    }
    
    @Override
    public void updateUI(@Nullable SignatureInformation signature, @NotNull ParameterInfoUIContext context) {
        if (signature == null) {
            context.setUIComponentEnabled(false);
            return;
        }
        
        String label = signature.getLabel();
        if (label == null) {
            context.setUIComponentEnabled(false);
            return;
        }
        
        // Display the signature
        context.setupUIComponentPresentation(
            label,
            0,
            label.length(),
            false,
            false,
            false,
            context.getDefaultParameterColor()
        );
    }
}

