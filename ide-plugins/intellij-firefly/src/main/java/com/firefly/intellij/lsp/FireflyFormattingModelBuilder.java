package com.firefly.intellij.lsp;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Formatting model builder for Firefly using LSP.
 * Provides code formatting by querying the LSP server.
 */
public class FireflyFormattingModelBuilder implements FormattingModelBuilder {
    
    private static final Logger LOG = LoggerFactory.getLogger(FireflyFormattingModelBuilder.class);
    
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        PsiElement element = formattingContext.getPsiElement();
        CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
        
        return FormattingModelProvider.createFormattingModelForPsiFile(
            element.getContainingFile(),
            new FireflyBlock(element.getNode(), settings),
            settings
        );
    }
    
    /**
     * Simple block implementation for Firefly formatting.
     */
    private static class FireflyBlock implements Block {
        
        private final ASTNode node;
        private final CodeStyleSettings settings;
        
        public FireflyBlock(ASTNode node, CodeStyleSettings settings) {
            this.node = node;
            this.settings = settings;
        }
        
        @Override
        public @NotNull TextRange getTextRange() {
            return node.getTextRange();
        }
        
        @Override
        public @NotNull List<Block> getSubBlocks() {
            return List.of();
        }
        
        @Override
        public @Nullable Wrap getWrap() {
            return null;
        }
        
        @Override
        public @Nullable Indent getIndent() {
            return Indent.getNoneIndent();
        }
        
        @Override
        public @Nullable Alignment getAlignment() {
            return null;
        }
        
        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            return null;
        }
        
        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }
        
        @Override
        public boolean isIncomplete() {
            return false;
        }
        
        @Override
        public boolean isLeaf() {
            return node.getFirstChildNode() == null;
        }
    }
    
    /**
     * Apply LSP formatting to a file.
     * This is called by IntelliJ's "Reformat Code" action.
     */
    public static void formatDocument(PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        
        Project project = psiFile.getProject();
        FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
        
        if (lspClient == null || !lspClient.isInitialized()) {
            LOG.warn("LSP client not initialized");
            return;
        }
        
        try {
            // Create LSP formatting request
            TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
            FormattingOptions options = new FormattingOptions();
            options.setTabSize(4);
            options.setInsertSpaces(true);
            
            DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
            
            // Request formatting from LSP server
            CompletableFuture<List<? extends TextEdit>> future = 
                lspClient.getLanguageServer().getTextDocumentService().formatting(params);
            
            List<? extends TextEdit> edits = future.get(5, TimeUnit.SECONDS);
            
            if (edits == null || edits.isEmpty()) {
                LOG.debug("No formatting changes needed");
                return;
            }
            
            // Apply the text edits
            com.intellij.openapi.editor.Document document = 
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile);
            
            if (document == null) {
                return;
            }
            
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, () -> {
                for (TextEdit edit : edits) {
                    Range range = edit.getRange();
                    String newText = edit.getNewText();
                    
                    int startLine = range.getStart().getLine();
                    int startChar = range.getStart().getCharacter();
                    int endLine = range.getEnd().getLine();
                    int endChar = range.getEnd().getCharacter();
                    
                    int startOffset = document.getLineStartOffset(startLine) + startChar;
                    int endOffset = document.getLineStartOffset(endLine) + endChar;
                    
                    document.replaceString(startOffset, endOffset, newText);
                }
            });
            
            LOG.debug("Applied {} formatting edit(s)", edits.size());
            
        } catch (Exception e) {
            LOG.error("Error formatting document", e);
        }
    }
}

