import com.walmartlabs.concord.runtime.v2.sdk.Task

import javax.inject.Named

@Named("myTask")
class MyTask implements Task {

    void hey(String name) {
        println "Hey, ${name}"
    }
}
