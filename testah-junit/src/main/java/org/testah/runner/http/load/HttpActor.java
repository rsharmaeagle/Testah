package org.testah.runner.http.load;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.routing.RoundRobinPool;
import org.testah.TS;
import org.testah.driver.http.requests.AbstractRequestDto;
import org.testah.driver.http.response.ResponseDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HttpActor extends UntypedAbstractActor {
    public static final int UNKNOWN_ERROR_STATUS = 700;
    private static HashMap<Long, List<ResponseDto>> results = new HashMap<Long, List<ResponseDto>>();
    private final ActorRef workerRouter;
    private final int nrOfWorkers;
    private final int numOfAttempts;
    private final Long hashId;

    /**
     * Constructor.
     *
     * @param nrOfWorkers   number of Akka workers
     * @param numOfAttempts number of attempts
     * @param hashId        Akka actor hash
     */
    public HttpActor(final int nrOfWorkers, final int numOfAttempts, final Long hashId) {
        this.hashId = hashId;
        results.put(hashId, new ArrayList<ResponseDto>());
        this.nrOfWorkers = nrOfWorkers;
        this.numOfAttempts = numOfAttempts;
        workerRouter = this.getContext()
                .actorOf(Props.create(HttpWorker.class).withRouter(new RoundRobinPool(nrOfWorkers)), "workerRouter");
    }

    /**
     * Get the responses for a particular Akka actor.
     *
     * @param hashId of Akka actor
     * @return list of responses
     */
    public static List<ResponseDto> getResults(final Long hashId) {
        HashMap<Long, List<ResponseDto>> resultsLocalPointer = getResults();
        if (!resultsLocalPointer.containsKey(hashId)) {
            resultsLocalPointer.put(hashId, new ArrayList<ResponseDto>());
        }
        return resultsLocalPointer.get(hashId);
    }

    /**
     * Get responses per Akka actor hash.
     *
     * @return map of hash id to list of responses.
     */
    public static HashMap<Long, List<ResponseDto>> getResults() {
        if (null == results) {
            resetResults();
        }
        return results;
    }

    public static void resetResults() {
        results = new HashMap<Long, List<ResponseDto>>();
    }

    /**
     * Implementation of UntypedAbstractActor.onReceiver(...).
     *
     * @see akka.actor.UntypedAbstractActor#onReceive(java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    public void onReceive(final Object message) throws Exception {
        try {
            if (message instanceof ResponseDto) {
                results.get(hashId).add((ResponseDto) message);
            } else if (message instanceof List) {
                for (final Class<?> test : (List<Class<?>>) message) {
                    workerRouter.tell(test, getSelf());
                }
            } else if (message instanceof AbstractRequestDto) {
                for (int start = 1; start <= numOfAttempts; start++) {
                    workerRouter.tell(message, getSelf());
                }
            } else if (message instanceof ConcurrentLinkedQueue) {
                for (int start = 1; start <= numOfAttempts; start++) {
                    workerRouter.tell(message, getSelf());
                }
            } else if (message instanceof Throwable) {
                results.get(hashId).add(getUnExpectedErrorResponseDto((Throwable) message));
            } else {
                TS.log().info("Issue, should not have made it here, message is " + message);

            }
        } catch (Throwable throwable) {
            TS.log().info("Throwable thrown in HttpActor.onReceive()", throwable);
            results.get(hashId).add(getUnExpectedErrorResponseDto(throwable));
        }
    }

    private ResponseDto getUnExpectedErrorResponseDto(final Throwable throwable) {
        ResponseDto response = new ResponseDto();
        response.setStatusCode(UNKNOWN_ERROR_STATUS);
        response.setStatusText(String.format("Unexpected Error[%s]", throwable.getMessage()));
        response.setResponseBody(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(throwable));
        return response;
    }

    public ActorRef getWorkerRouter() {

        return workerRouter;
    }

    public int getNrOfWorkers() {
        return nrOfWorkers;
    }

}
