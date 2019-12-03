package com.splunk.splunkjenkins.console;

import hudson.MarkupText;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class LabelMarkupText extends MarkupText {
    private static final String PARALLEL_BRANCH_LABEL = "Branch: ";
    private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private static final Logger LOG = Logger.getLogger(LabelMarkupText.class.getName());
    private static final String PARALLEL_LABEL = "parallel_label";

    //remembered enclosing label
    private String encloseLabel = null;
    private SoftReference<Map<String, String>> encloseLabelRef = new SoftReference<>(new HashMap<String, String>());
    private String annotation = null;

    public LabelMarkupText() {
        super("");
    }

    @Override
    public void addMarkup(int startPos, int endPos, String startTag, String endTag) {
        parseTagLabel(startTag + endTag);
    }

    /**
     * @see org.jenkinsci.plugins.workflow.job.console.NewNodeConsoleNote
     * @see hudson.console.HyperlinkNote
     * @see org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApprovalNote
     */
    private void parseTagLabel(String tag) {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(tag);
        }
        annotation = "";
        try {
            Element node = factory.newDocumentBuilder().parse(new StringInputStream(tag)).getDocumentElement();
            // hpyerlink
            String href = node.getAttribute("href");
            if (isNotEmpty(href)) {
                annotation = "href=" + href;
            }
            String nodeId = node.getAttribute("nodeId");
            // NewNodeConsoleNote
            if (isNotEmpty(nodeId)) {
                // encloseLabelRef lost in gc 
                Map<String, String> encloseLabels = encloseLabelRef.get();
                if (encloseLabels == null) {
                    return;
                }
                if (node.getAttribute("startId").length() > 0) {
                    // BlockEndNode or BlockStartNode
                    encloseLabel = null;
                    String label = node.getAttribute("label");
                    if (label.startsWith(PARALLEL_BRANCH_LABEL)) {
                        encloseLabels.put(nodeId, label.substring(PARALLEL_BRANCH_LABEL.length()));
                    }
                } else {
                    String enclosingId = node.getAttribute("enclosingId");
                    if (isNotEmpty(enclosingId)) {
                        //pipeline step  (not block level)
                        String nodeLabel = encloseLabels.get(enclosingId);
                        if (nodeLabel != null) {
                            // update the label
                            encloseLabels.put(nodeId, nodeLabel);
                            encloseLabel = PARALLEL_LABEL + "=\"" + StringEscapeUtils.escapeJava(nodeLabel) + "\"";
                        } else {
                            encloseLabel = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("failed to parse html console note " + e);
        }
    }

    public void write(OutputStream out) throws IOException {
        if (isNotEmpty(annotation)) {
            out.write(annotation.getBytes());
            out.write(' ');
        }
    }

    public void writePreviousLabel(OutputStream out) throws IOException {
        if (isNotEmpty(encloseLabel)) {
            out.write(encloseLabel.getBytes());
            out.write(' ');
        }
    }
}
