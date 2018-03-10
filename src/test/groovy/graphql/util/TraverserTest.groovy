package graphql.util

import spock.lang.Specification

class TraverserTest extends Specification {

    /**
     *          0
     *       1      2
     *       3    4  5
     */
    def root = [
            [number: 0, children: [
                    [number: 1, children: [
                            [number: 3, children: []]
                    ]],
                    [number: 2, children: [
                            [number: 4, children: []],
                            [number: 5, children: []]
                    ]]]
            ]
    ]

    def "test depth-first traversal"() {
        given:
        def preOrderNodes = []
        def postOrderNodes = []
        def visitor = [
                enter: { TraverserContext context ->
                    preOrderNodes << context.thisNode().number
                    println "enter:$preOrderNodes"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    postOrderNodes << context.thisNode().number
                    println "leave:$postOrderNodes"
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        preOrderNodes == [0, 1, 3, 2, 4, 5]
        postOrderNodes == [3, 1, 4, 5, 2, 0]
    }


    def "test breadth-first traversal"() {
        given:
        def enterData = []
        def leaveData = []
        def visitor = [
                enter: { TraverserContext context ->
                    enterData << context.thisNode().number
                    println "enter:$enterData"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    leaveData << context.thisNode().number
                    println "leave:$leaveData"
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        enterData == [0, 1, 2, 3, 4, 5]
        leaveData == [0, 1, 2, 3, 4, 5]
    }

    def "quit traversal immediately"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.QUIT
                }
        ] as TraverserVisitor

        when:
        Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0]


        when:
        initialData.clear()
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0]

    }

    def "quit traversal in first leave"() {
        given:
        def initialData = new ArrayList()
        def leaveCount = 0
        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    leaveCount++
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 3]
        leaveCount == 1

    }

    def "abort subtree traversal depth-first"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    if ([1, 2].contains(context.thisNode().number)) return TraversalControl.ABORT
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2]

    }

    def "abort subtree traversal breadth-first"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    if ([2].contains(context.thisNode().number)) return TraversalControl.ABORT
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },

        ] as TraverserVisitor

        when:
        Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2, 3]
    }

    static class Node {
        int number
        List<Node> children = new ArrayList<>()
    }

    def "simple cycle"() {
        given:
        def cycleRoot = new Node(number: 0)
        cycleRoot.children.add(cycleRoot)

        def visitor = Mock(TraverserVisitor)
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(cycleRoot, visitor)

        then:
        1 * visitor.enter(_) >> TraversalControl.CONTINUE
        1 * visitor.leave(_) >> TraversalControl.CONTINUE
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == cycleRoot })
    }

    def "more complex cycles"() {
        given:
        def cycleRoot = new Node(number: 0)
        cycleRoot.children.add(new Node(number: 1))

        def node2 = new Node(number: 2)
        cycleRoot.children.add(node2)
        node2.children.add(node2)

        def node3 = new Node(number: 3)
        cycleRoot.children.add(node3)
        def node5 = new Node(number: 5)
        node3.children.add(node5)
        node5.children.add(cycleRoot)

        def visitor = Mock(TraverserVisitor)
        visitor.enter(_) >> TraversalControl.CONTINUE
        visitor.leave(_) >> TraversalControl.CONTINUE
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(cycleRoot, visitor)

        then:
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == cycleRoot })
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == node2 })
        0 * visitor.backRef(_)
    }

}
