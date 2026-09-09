package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;

/** Evaluates explicit process-budget and post-baseline idle-growth thresholds. */
final class ResourceCriteria {
    private ResourceCriteria() {}

    static List<String> evaluate(LoadSettings settings, ResourceSampler.Summary resources) {
        var failures = new ArrayList<String>();
        long rss = Math.max(resources.sampledPeakRssBytes(), resources.latest().peakRssBytes());
        if (settings.maximumRssBytes() > 0) {
            if (rss < 0) failures.add("rss-observation-unavailable");
            else if (rss > settings.maximumRssBytes()) failures.add("rss-budget-exceeded");
        }
        if (settings.maximumHeapBytes() > 0 && resources.sampledPeakHeapBytes() > settings.maximumHeapBytes())
            failures.add("heap-budget-exceeded");
        if (settings.maximumDescriptorGrowth() >= 0) {
            if (!resources.baselineRecorded()
                    || resources.baseline().fileDescriptors() < 0
                    || resources.latest().fileDescriptors() < 0) failures.add("descriptor-baseline-unavailable");
            else if (resources.latest().fileDescriptors() - resources.baseline().fileDescriptors()
                    > settings.maximumDescriptorGrowth()) failures.add("descriptor-growth-exceeded");
        }
        if (settings.maximumThreadGrowth() >= 0) {
            if (!resources.baselineRecorded()) failures.add("thread-baseline-unavailable");
            else if (resources.latest().platformThreads() - resources.baseline().platformThreads()
                    > settings.maximumThreadGrowth()) failures.add("platform-thread-growth-exceeded");
        }
        return List.copyOf(failures);
    }
}
