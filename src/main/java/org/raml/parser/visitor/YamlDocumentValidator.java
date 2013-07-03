package org.raml.parser.visitor;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.raml.parser.rule.DefaultTupleRule;
import org.raml.parser.rule.ITupleRule;
import org.raml.parser.rule.ValidationResult;
import org.raml.parser.utils.RuleFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class YamlDocumentValidator implements NodeHandler
{

    private Class<?> documentClass;

    private Stack<ITupleRule<?, ?>> ruleContext = new Stack<ITupleRule<?, ?>>();

    private List<ValidationResult> errorMessage = new ArrayList<ValidationResult>();

    public YamlDocumentValidator(Class<?> documentClass)
    {
        this.documentClass = documentClass;

    }

    public List<ValidationResult> validate(String content)
    {
        Yaml yamlParser = new Yaml();

        try
        {
            NodeVisitor nodeVisitor = new NodeVisitor(this);
            for (Node data : yamlParser.composeAll(new StringReader(content)))
            {
                if (data instanceof MappingNode)
                {
                    nodeVisitor.visitDocument((MappingNode) data);
                }
                else
                {
                    //   errorMessage.add(ValidationResult.createErrorResult(EMPTY_DOCUMENT_MESSAGE));
                }
            }

        }
        catch (YAMLException ex)
        {
            // errorMessage.add(ValidationResult.createErrorResult(ex.getMessage()));
        }
        return errorMessage;
    }


    @Override
    public void onMappingNodeStart(MappingNode mappingNode)
    {

    }

    @Override
    public void onMappingNodeEnd(MappingNode mappingNode)
    {

    }

    @Override
    @SuppressWarnings("unchecked")
    public void onSequenceStart(SequenceNode node, TupleType tupleType)
    {
        List<ValidationResult> result;
        ITupleRule<?, ?> peek = ruleContext.peek();

        switch (tupleType)
        {
            case VALUE:
                result = ((ITupleRule<?, SequenceNode>) peek).validateValue(node);
                break;
            default:
                result = ((ITupleRule<SequenceNode, ?>) peek).validateKey(node);
                break;
        }
        addErrorMessageIfRequired(node, result);
    }

    @Override
    public void onSequenceEnd(SequenceNode node, TupleType tupleType)
    {

    }

    @Override
    @SuppressWarnings("unchecked")
    public void onScalar(ScalarNode node, TupleType tupleType)
    {
        List<ValidationResult> result;
        ITupleRule<?, ?> peek = ruleContext.peek();

        switch (tupleType)
        {
            case VALUE:
                result = ((ITupleRule<?, ScalarNode>) peek).validateValue(node);
                break;

            default:
                result = ((ITupleRule<ScalarNode, ?>) peek).validateKey(node);
                break;
        }
        addErrorMessageIfRequired(node, result);
    }

    private void addErrorMessageIfRequired(Node node, List<ValidationResult> result)
    {
        for (ValidationResult validationResult : result)
        {
            if (!validationResult.isValid())
            {
                errorMessage.add(validationResult);
            }
        }
    }

    @Override
    public void onDocumentStart(MappingNode node)
    {
        ruleContext.push(buildDocumentRule());
    }

    @Override
    public void onDocumentEnd(MappingNode node)
    {
        ITupleRule<?, ?> pop = ruleContext.pop();

        List<ValidationResult> onRuleEnd = pop.onRuleEnd();
        addErrorMessageIfRequired(node, onRuleEnd);

    }

    @Override
    public void onTupleEnd(NodeTuple nodeTuple)
    {
        ITupleRule<?, ?> rule = ruleContext.pop();
        if (rule != null)
        {
            List<ValidationResult> onRuleEnd = rule.onRuleEnd();
            addErrorMessageIfRequired(nodeTuple.getKeyNode(), onRuleEnd);
        }
        else
        {
            throw new IllegalStateException("Unexpected ruleContext state");
        }
    }

    @Override
    public void onTupleStart(NodeTuple nodeTuple)
    {

        ITupleRule<?, ?> tupleRule = ruleContext.peek();
        if (tupleRule != null)
        {
            ITupleRule<?, ?> rule = tupleRule.getRuleForTuple(nodeTuple);
            ruleContext.push(rule);
        }
        else
        {
            throw new IllegalStateException("Unexpected ruleContext state");
        }

    }

    private DefaultTupleRule<Node, MappingNode> buildDocumentRule()
    {
        DefaultTupleRule<Node, MappingNode> documentRule = new DefaultTupleRule<Node, MappingNode>();
        Field[] declaredFields = documentClass.getDeclaredFields();
        Map<String, ITupleRule<?, ?>> innerRules = new HashMap<String, ITupleRule<?, ?>>();
        for (Field declaredField : declaredFields)
        {
            ITupleRule<?, ?> iTupleRule = RuleFactory.INSTANCE.createRuleFor(declaredField);
            if (iTupleRule != null) {
                iTupleRule.setParentTupleRule(documentRule);
                innerRules.put(declaredField.getName(), iTupleRule);
            }
        }
        documentRule.setNestedRules(innerRules);
        return documentRule;
    }

}