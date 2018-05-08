import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Container, Header, Segment } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { EditRepositoryActivity } from '../../organisms';

interface RouteProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

class AddRepositoryPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, projectName } = this.props.match.params;

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/project/${projectName}/repository`}>
                            {projectName}
                        </Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>Add Repository</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Segment basic={true}>
                    <Container text={true}>
                        <Header>
                            <Header.Content>Add a Repository</Header.Content>
                            <Header.Subheader>Register an existing GIT repository</Header.Subheader>
                        </Header>
                        <EditRepositoryActivity orgName={orgName} projectName={projectName} />
                    </Container>
                </Segment>
            </>
        );
    }
}

export default withRouter(AddRepositoryPage);